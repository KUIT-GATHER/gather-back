package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

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
                .build();
    }

    private Posting postingWithStatus(PostingStatus status) {
        return postingRepository.save(
                Posting.builder()
                        .title("테스트 공고")
                        .status(status)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .category(PostingCategory.ENVIRONMENT)
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
                        .build());
    }

    private Posting posting(PostingStatus status, Long regionId, LocalDate activityDate) {
        return Posting.builder()
                .title("테스트 공고")
                .status(status)
                .activityDate(activityDate)
                .regionId(regionId)
                .category(PostingCategory.ENVIRONMENT)
                .build();
    }

    private Posting postingWithCategory(PostingCategory category) {
        return Posting.builder()
                .title("테스트 공고")
                .status(PostingStatus.RECRUITING)
                .activityDate(LocalDate.of(2026, 7, 15))
                .category(category)
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
                        .build());
    }
}
