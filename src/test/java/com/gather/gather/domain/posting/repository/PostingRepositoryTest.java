package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingLocation;
import com.gather.gather.domain.posting.entity.PostingSource;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PostingRepository#search}의 실제 DB 동작 검증. regionIds가 null이 아니라 빈 리스트로 들어왔을 때(존재하지 않는
 * regionId 필터) 예외 없이 빈 결과를 반환하는지, status가 null일 때 RECRUITING/CLOSED가 우선순위 정렬되어 반환되고 COMPLETED는
 * 제외되는지가 핵심 검증 대상이다.
 */
@SpringBootTest
@Transactional
class PostingRepositoryTest {

    @Autowired private PostingRepository postingRepository;
    @Autowired private PostingLocationRepository postingLocationRepository;

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    /**
     * PostingRepositoryImpl이 마감 여부 판정에 Asia/Seoul 기준 오늘 날짜를 쓰므로, 테스트의 "오늘" 기준도 동일한 시간대로 맞춰야 CI
     * 서버(UTC)와 자정 경계에서 날짜가 어긋나 간헐적으로 실패하는 일을 막을 수 있다.
     */
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Test
    void search_filtersByStatus_whenRegionAndDateFiltersAreNull() {
        postingRepository.save(posting(PostingStatus.RECRUITING, 1L, LocalDate.of(2026, 7, 1)));
        postingRepository.save(posting(PostingStatus.CLOSED, 1L, LocalDate.of(2026, 7, 1)));

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING, null, null, null, null, null, PAGEABLE);

        assertThat(result.getContent()).allMatch(p -> p.getStatus() == PostingStatus.RECRUITING);
    }

    @Test
    void search_returnsEmpty_withoutException_whenRegionIdsIsEmptyList() {
        postingRepository.save(posting(PostingStatus.RECRUITING, 1L, LocalDate.of(2026, 7, 1)));

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING, List.of(), null, null, null, null, PAGEABLE);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void search_filtersByRegionIds_whenProvided() {
        Posting matching =
                postingRepository.save(
                        posting(PostingStatus.RECRUITING, 42L, LocalDate.of(2026, 7, 1)));
        postingRepository.save(posting(PostingStatus.RECRUITING, 43L, LocalDate.of(2026, 7, 1)));

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING, List.of(42L), null, null, null, null, PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(matching.getId());
    }

    @Test
    void search_filtersByCategory_whenProvided() {
        Posting matching = postingRepository.save(postingWithCategory(PostingCategory.ENVIRONMENT));
        postingRepository.save(postingWithCategory(PostingCategory.WELFARE));

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING,
                        null,
                        null,
                        null,
                        null,
                        PostingCategory.ENVIRONMENT,
                        PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(matching.getId());
    }

    @Test
    void search_filtersByNoticeDateRange_whenProvided() {
        Posting inRange =
                save(
                        PostingStatus.RECRUITING,
                        LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 7, 20));
        save(PostingStatus.RECRUITING, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING,
                        null,
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31),
                        null,
                        null,
                        PAGEABLE);

        assertThat(result.getContent()).extracting(Posting::getId).containsExactly(inRange.getId());
    }

    @Test
    void search_matchesKeyword_whenTitleContainsKeyword() {
        Posting matching = postingWithTitleAndOrg("동구 환경정화 봉사", "울산 동구청");
        postingWithTitleAndOrg("무관한 공고", "다른 기관");

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING, null, null, null, "환경", null, PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(matching.getId());
    }

    @Test
    void search_matchesKeyword_whenRecruitOrgContainsKeyword() {
        Posting matching = postingWithTitleAndOrg("봉사 공고", "울산 동구청");
        postingWithTitleAndOrg("다른 공고", "부산 진구청");

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING, null, null, null, "동구청", null, PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(matching.getId());
    }

    @Test
    void search_returnsEmpty_whenKeywordMatchesNothing() {
        postingWithTitleAndOrg("봉사 공고", "울산 동구청");

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING, null, null, null, "존재하지않는키워드", null, PAGEABLE);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void search_returnsRecruitingAndClosedButNotCompleted_whenStatusIsNull() {
        Posting recruiting = postingWithStatus(PostingStatus.RECRUITING);
        Posting closed = postingWithStatus(PostingStatus.CLOSED);
        postingWithStatus(PostingStatus.COMPLETED);

        var result = postingRepository.search(null, null, null, null, null, null, PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactlyInAnyOrder(recruiting.getId(), closed.getId());
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    void search_returnsOnlyCompleted_whenStatusIsExplicitlyCompleted() {
        Posting completed = postingWithStatus(PostingStatus.COMPLETED);
        postingWithStatus(PostingStatus.RECRUITING);
        postingWithStatus(PostingStatus.CLOSED);

        var result =
                postingRepository.search(
                        PostingStatus.COMPLETED, null, null, null, null, null, PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(completed.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void search_ordersRecruitingGroupBeforeClosedGroup_regardlessOfSecondarySort() {
        // id 오름차순 정렬이면 원래는 closed가 먼저 나와야 하지만, 모집중 우선순위가 이를 덮어써야 한다.
        Posting closed = postingWithStatus(PostingStatus.CLOSED);
        Posting recruiting = postingWithStatus(PostingStatus.RECRUITING);
        Pageable idAscending = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "id"));

        var result = postingRepository.search(null, null, null, null, null, null, idAscending);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(recruiting.getId(), closed.getId());
    }

    @Test
    void search_appliesSecondarySort_withinEachPriorityGroup() {
        Posting recruitingLow = postingWithStatusAndApplicantCount(PostingStatus.RECRUITING, 1);
        Posting recruitingHigh = postingWithStatusAndApplicantCount(PostingStatus.RECRUITING, 9);
        Posting closedLow = postingWithStatusAndApplicantCount(PostingStatus.CLOSED, 2);
        Posting closedHigh = postingWithStatusAndApplicantCount(PostingStatus.CLOSED, 8);
        Pageable applicantCountDescending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "applicantCount"));

        var result =
                postingRepository.search(
                        null, null, null, null, null, null, applicantCountDescending);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(
                        recruitingHigh.getId(),
                        recruitingLow.getId(),
                        closedHigh.getId(),
                        closedLow.getId());
    }

    @Test
    void search_ordersOpenNoticeRecruitingBeforeStaleNoticeRecruiting_whenStatusIsNull() {
        // 외부 공공데이터 API 동기화 지연으로 마감일이 지났는데도 status가 아직 RECRUITING인 시나리오.
        Posting staleRecruiting = save(PostingStatus.RECRUITING, null, TODAY.minusDays(1));
        Posting openRecruiting = save(PostingStatus.RECRUITING, null, TODAY.plusDays(5));
        Posting closed = save(PostingStatus.CLOSED, null, TODAY.minusDays(2));
        Pageable noticeEndAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "noticeEndDate"));

        var result =
                postingRepository.search(null, null, null, null, null, null, noticeEndAscending);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(openRecruiting.getId(), closed.getId(), staleRecruiting.getId());
    }

    @Test
    void search_ordersOpenNoticeBeforeStaleNotice_whenStatusIsRecruiting() {
        Posting staleRecruiting = save(PostingStatus.RECRUITING, null, TODAY.minusDays(3));
        Posting openRecruitingFar = save(PostingStatus.RECRUITING, null, TODAY.plusDays(10));
        Posting openRecruitingNear = save(PostingStatus.RECRUITING, null, TODAY.plusDays(2));
        Pageable noticeEndAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "noticeEndDate"));

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING, null, null, null, null, null, noticeEndAscending);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(
                        openRecruitingNear.getId(),
                        openRecruitingFar.getId(),
                        staleRecruiting.getId());
    }

    @Test
    void search_ordersNullNoticeEndDateAfterOpenNoticesButBeforeStaleNotices_whenStatusIsNull() {
        // 마감일이 없는 상시모집 공고는 DB의 NULL 정렬 정책상 오름차순 정렬에서 최상단에 노출될 수 있으므로,
        // 마감일이 있고 아직 지나지 않은 공고 다음, 이미 마감된 공고보다는 앞에 오도록 강제한다.
        Posting dueToday = save(PostingStatus.RECRUITING, null, TODAY);
        Posting dueLater = save(PostingStatus.RECRUITING, null, TODAY.plusDays(3));
        Posting noDeadline = save(PostingStatus.RECRUITING, null, null);
        Posting staleRecruiting = save(PostingStatus.RECRUITING, null, TODAY.minusDays(1));
        Pageable noticeEndAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "noticeEndDate"));

        var result =
                postingRepository.search(null, null, null, null, null, null, noticeEndAscending);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(
                        dueToday.getId(),
                        dueLater.getId(),
                        noDeadline.getId(),
                        staleRecruiting.getId());
    }

    @Test
    void search_treatsNoticeEndDateOfTodayAsStillOpen() {
        Posting dueToday = save(PostingStatus.RECRUITING, null, TODAY);
        Posting staleYesterday = save(PostingStatus.RECRUITING, null, TODAY.minusDays(1));
        Pageable noticeEndAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "noticeEndDate"));

        var result =
                postingRepository.search(
                        PostingStatus.RECRUITING, null, null, null, null, null, noticeEndAscending);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(dueToday.getId(), staleYesterday.getId());
    }

    @Test
    void search_paginatesCorrectly_acrossRecruitingClosedGroupBoundary() {
        Posting r1 = postingWithStatus(PostingStatus.RECRUITING);
        Posting r2 = postingWithStatus(PostingStatus.RECRUITING);
        Posting c1 = postingWithStatus(PostingStatus.CLOSED);
        Posting c2 = postingWithStatus(PostingStatus.CLOSED);
        Posting c3 = postingWithStatus(PostingStatus.CLOSED);
        Pageable idAscending = PageRequest.of(0, 3, Sort.by(Sort.Direction.ASC, "id"));

        var firstPage = postingRepository.search(null, null, null, null, null, null, idAscending);
        var secondPage =
                postingRepository.search(null, null, null, null, null, null, idAscending.next());

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .extracting(Posting::getId)
                .containsExactly(r1.getId(), r2.getId(), c1.getId());
        assertThat(secondPage.getContent())
                .extracting(Posting::getId)
                .containsExactly(c2.getId(), c3.getId());
    }

    @Test
    void
            existsByTitleAndActivityDate_returnsTrue_whenTitleAndActivityDateMatchRegardlessOfSource() {
        postingRepository.save(postingWithTitleAndOrg("동구 환경정화 봉사", "울산 동구청"));

        boolean exists =
                postingRepository.existsByTitleAndActivityDate(
                        "동구 환경정화 봉사", LocalDate.of(2026, 7, 15));

        assertThat(exists).isTrue();
    }

    @Test
    void existsByTitleAndActivityDate_returnsFalse_whenActivityDateDiffers() {
        postingRepository.save(postingWithTitleAndOrg("동구 환경정화 봉사", "울산 동구청"));

        boolean exists =
                postingRepository.existsByTitleAndActivityDate(
                        "동구 환경정화 봉사", LocalDate.of(2026, 7, 16));

        assertThat(exists).isFalse();
    }

    @Test
    void deactivateExpired_deactivatesPosting_whenActEndDateIsBeforeToday() {
        Posting posting =
                postingRepository.save(
                        lifecyclePosting(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10)));

        int count =
                postingRepository.deactivateExpired(LocalDate.of(2026, 7, 20), LocalDateTime.now());

        assertThat(count).isEqualTo(1);
        assertThat(postingRepository.findById(posting.getId()).orElseThrow().getIsActive())
                .isFalse();
    }

    @Test
    void deactivateExpired_deactivatesPosting_whenActEndDateMissingAndActivityDateBeforeToday() {
        Posting posting = postingRepository.save(lifecyclePosting(LocalDate.of(2026, 7, 1), null));

        int count =
                postingRepository.deactivateExpired(LocalDate.of(2026, 7, 20), LocalDateTime.now());

        assertThat(count).isEqualTo(1);
        assertThat(postingRepository.findById(posting.getId()).orElseThrow().getIsActive())
                .isFalse();
    }

    @Test
    void deactivateExpired_doesNotDeactivate_whenActEndDateMissingAndActivityDateAfterToday() {
        Posting posting = postingRepository.save(lifecyclePosting(LocalDate.of(2026, 7, 25), null));

        int count =
                postingRepository.deactivateExpired(LocalDate.of(2026, 7, 20), LocalDateTime.now());

        assertThat(count).isZero();
        assertThat(postingRepository.findById(posting.getId()).orElseThrow().getIsActive())
                .isTrue();
    }

    @Test
    void clearExpiredContent_clearsContent_whenInactiveAndCutoffPassed() {
        Posting posting =
                postingRepository.save(
                        inactiveLifecyclePosting(
                                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5)));

        int count =
                postingRepository.clearExpiredContent(
                        LocalDate.of(2026, 7, 1), LocalDateTime.now());

        assertThat(count).isEqualTo(1);
        assertThat(postingRepository.findById(posting.getId()).orElseThrow().getContent()).isNull();
    }

    @Test
    void clearExpiredContent_doesNotClear_whenStillActive() {
        Posting posting =
                postingRepository.save(
                        lifecyclePosting(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5)));

        int count =
                postingRepository.clearExpiredContent(
                        LocalDate.of(2026, 7, 1), LocalDateTime.now());

        assertThat(count).isZero();
        assertThat(postingRepository.findById(posting.getId()).orElseThrow().getContent())
                .isNotNull();
    }

    @Test
    void clearExpiredContent_clearsContent_whenExactlyAtCutoffDate() {
        Posting posting =
                postingRepository.save(
                        inactiveLifecyclePosting(
                                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1)));

        int count =
                postingRepository.clearExpiredContent(
                        LocalDate.of(2026, 7, 1), LocalDateTime.now());

        assertThat(count).isEqualTo(1);
        assertThat(postingRepository.findById(posting.getId()).orElseThrow().getContent()).isNull();
    }

    @Test
    void clearExpiredContent_doesNotClear_whenWithinCutoff() {
        Posting posting =
                postingRepository.save(
                        inactiveLifecyclePosting(
                                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 25)));

        int count =
                postingRepository.clearExpiredContent(
                        LocalDate.of(2026, 7, 20), LocalDateTime.now());

        assertThat(count).isZero();
        assertThat(postingRepository.findById(posting.getId()).orElseThrow().getContent())
                .isNotNull();
    }

    private Posting lifecyclePosting(LocalDate activityDate, LocalDate actEndDate) {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .content("본문 내용")
                .activityDate(activityDate)
                .actEndDate(actEndDate)
                .category(PostingCategory.ENVIRONMENT)
                .isActive(true)
                .source(PostingSource.API_1365)
                .build();
    }

    private Posting inactiveLifecyclePosting(LocalDate activityDate, LocalDate actEndDate) {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.CLOSED)
                .content("본문 내용")
                .activityDate(activityDate)
                .actEndDate(actEndDate)
                .category(PostingCategory.ENVIRONMENT)
                .isActive(false)
                .source(PostingSource.API_1365)
                .build();
    }

    private Posting postingWithStatus(PostingStatus status) {
        return postingRepository.save(
                Posting.builder()
                        .title("테스트 공고")
                        .status(status)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .category(PostingCategory.ENVIRONMENT)
                        .source(PostingSource.API_1365)
                        .build());
    }

    private Posting postingWithStatusAndApplicantCount(PostingStatus status, int applicantCount) {
        return postingRepository.save(
                Posting.builder()
                        .title("테스트 공고")
                        .status(status)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .applicantCount(applicantCount)
                        .category(PostingCategory.ENVIRONMENT)
                        .source(PostingSource.API_1365)
                        .build());
    }

    private Posting save(PostingStatus status, LocalDate noticeStart, LocalDate noticeEnd) {
        return postingRepository.save(
                Posting.builder()
                        .title("테스트 공고")
                        .status(status)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .noticeStartDate(noticeStart)
                        .noticeEndDate(noticeEnd)
                        .category(PostingCategory.ENVIRONMENT)
                        .source(PostingSource.API_1365)
                        .build());
    }

    private Posting posting(PostingStatus status, Long regionId, LocalDate activityDate) {
        return Posting.builder()
                .title("테스트 공고")
                .status(status)
                .activityDate(activityDate)
                .regionId(regionId)
                .category(PostingCategory.ENVIRONMENT)
                .source(PostingSource.API_1365)
                .build();
    }

    private Posting postingWithCategory(PostingCategory category) {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(category)
                .source(PostingSource.API_1365)
                .build();
    }

    private Posting postingWithTitleAndOrg(String title, String recruitOrg) {
        return postingRepository.save(
                Posting.builder()
                        .title(title)
                        .recruitOrg(recruitOrg)
                        .status(PostingStatus.RECRUITING)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .category(PostingCategory.ENVIRONMENT)
                        .source(PostingSource.API_1365)
                        .build());
    }

    // ── searchForMap(#186) ──

    @Test
    void searchForMap_matchesPosting_whenPrimaryLocationIsWithinBounds() {
        Posting inBounds = postingWithLatLng(new BigDecimal("37.55"), new BigDecimal("126.90"));
        postingWithLatLng(new BigDecimal("35.10"), new BigDecimal("129.00"));

        List<Posting> result =
                postingRepository.searchForMap(
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("37.50"),
                        new BigDecimal("126.80"),
                        new BigDecimal("37.60"),
                        new BigDecimal("126.95"));

        assertThat(result).extracting(Posting::getId).containsExactly(inBounds.getId());
    }

    @Test
    void searchForMap_excludesPosting_whenNoLocationHasValidLatLng() {
        Posting noLocation =
                postingRepository.save(
                        Posting.builder()
                                .title("위치 없는 공고")
                                .status(PostingStatus.RECRUITING)
                                .activityDate(LocalDate.of(2026, 7, 15))
                                .category(PostingCategory.ENVIRONMENT)
                                .source(PostingSource.API_1365)
                                .build());

        List<Posting> result =
                postingRepository.searchForMap(
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("37.50"),
                        new BigDecimal("126.80"),
                        new BigDecimal("37.60"),
                        new BigDecimal("126.95"));

        assertThat(result).extracting(Posting::getId).doesNotContain(noLocation.getId());
    }

    @Test
    void searchForMap_matchesPosting_whenOnlySecondaryLocationIsWithinBounds() {
        Posting matching = postingWithLatLng(new BigDecimal("35.10"), new BigDecimal("129.00"));
        postingLocationRepository.save(
                PostingLocation.create(
                        matching.getId(),
                        2,
                        "서울 어딘가",
                        new BigDecimal("37.55"),
                        new BigDecimal("126.90")));

        List<Posting> result =
                postingRepository.searchForMap(
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("37.50"),
                        new BigDecimal("126.80"),
                        new BigDecimal("37.60"),
                        new BigDecimal("126.95"));

        assertThat(result).extracting(Posting::getId).containsExactly(matching.getId());
    }

    @Test
    void searchForMap_appliesActivityDateOverlapFilter() {
        Posting withinRange =
                postingWithLatLngAndActivityDates(
                        LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 25));
        postingWithLatLngAndActivityDates(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));

        List<Posting> result =
                postingRepository.searchForMap(
                        null,
                        LocalDate.of(2026, 8, 18),
                        LocalDate.of(2026, 8, 27),
                        null,
                        new BigDecimal("37.50"),
                        new BigDecimal("126.80"),
                        new BigDecimal("37.60"),
                        new BigDecimal("126.95"));

        assertThat(result).extracting(Posting::getId).containsExactly(withinRange.getId());
    }

    @Test
    void searchForMap_excludesCompletedStatus() {
        Posting completed =
                postingRepository.save(
                        Posting.builder()
                                .title("완료된 공고")
                                .status(PostingStatus.COMPLETED)
                                .activityDate(LocalDate.of(2026, 7, 15))
                                .category(PostingCategory.ENVIRONMENT)
                                .source(PostingSource.API_1365)
                                .latitude(new BigDecimal("37.55"))
                                .longitude(new BigDecimal("126.90"))
                                .build());

        List<Posting> result =
                postingRepository.searchForMap(
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("37.50"),
                        new BigDecimal("126.80"),
                        new BigDecimal("37.60"),
                        new BigDecimal("126.95"));

        assertThat(result).extracting(Posting::getId).doesNotContain(completed.getId());
    }

    private Posting postingWithLatLng(BigDecimal latitude, BigDecimal longitude) {
        return postingRepository.save(
                Posting.builder()
                        .title("지도 테스트 공고")
                        .status(PostingStatus.RECRUITING)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .category(PostingCategory.ENVIRONMENT)
                        .source(PostingSource.API_1365)
                        .latitude(latitude)
                        .longitude(longitude)
                        .build());
    }

    private Posting postingWithLatLngAndActivityDates(
            LocalDate actStartDate, LocalDate actEndDate) {
        return postingRepository.save(
                Posting.builder()
                        .title("지도 활동일 테스트 공고")
                        .status(PostingStatus.RECRUITING)
                        .activityDate(actStartDate)
                        .actStartDate(actStartDate)
                        .actEndDate(actEndDate)
                        .category(PostingCategory.ENVIRONMENT)
                        .source(PostingSource.API_1365)
                        .latitude(new BigDecimal("37.55"))
                        .longitude(new BigDecimal("126.90"))
                        .build());
    }
}
