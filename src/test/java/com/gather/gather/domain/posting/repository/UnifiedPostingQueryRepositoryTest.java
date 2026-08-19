package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingSource;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository.CursorSearchResult;
import com.gather.gather.domain.recruit.entity.MeetingRecruit;
import com.gather.gather.domain.recruit.repository.MeetingRecruitRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UnifiedPostingQueryRepository#search}의 키셋(커서) 페이지네이션 검증.
 *
 * <p>정렬 우선순위(마감임박 stale-bucket 보정 포함)는 기존 OFFSET 구현과 동일하게 유지되는지, 그리고 커서로 이어붙였을 때 항목이
 * 중복·누락 없이 순서대로 나오는지를 함께 검증한다.
 */
@SpringBootTest
@Transactional
class UnifiedPostingQueryRepositoryTest {

    @Autowired private UnifiedPostingQueryRepository unifiedPostingQueryRepository;
    @Autowired private PostingRepository postingRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private MeetingRecruitRepository meetingRecruitRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;

    /**
     * UnifiedPostingQueryRepository가 마감 여부 판정에 Asia/Seoul 기준 오늘 날짜를 쓰므로, 테스트의 "오늘" 기준도 동일한 시간대로 맞춰야
     * CI 서버(UTC)와 자정 경계에서 날짜가 어긋나 간헐적으로 실패하는 일을 막을 수 있다.
     */
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    private static final Sort ID_DESC = Sort.by(Sort.Direction.DESC, "id");
    private static final Sort APPLY_DEADLINE_ASC = Sort.by(Sort.Direction.ASC, "applyDeadlineAt");

    // ── 기존 정렬 우선순위 회귀 테스트(OFFSET → 커서 전환 후에도 동일하게 유지되는지) ──

    @Test
    void search_pushesStaleRecruitingBehindOpenRecruiting_whenSortingByApplyDeadlineAtAscending() {
        Posting staleRecruiting = save(PostingStatus.RECRUITING, TODAY.minusDays(1));
        Posting openRecruiting = save(PostingStatus.RECRUITING, TODAY.plusDays(5));
        Posting closed = save(PostingStatus.CLOSED, TODAY.minusDays(2));

        CursorSearchResult result = search(null, APPLY_DEADLINE_ASC);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(openRecruiting.getId(), closed.getId(), staleRecruiting.getId());
    }

    @Test
    void search_ordersNullDeadlineAfterOpenButBeforeStale_whenSortingByApplyDeadlineAtAscending() {
        Posting dueToday = save(PostingStatus.RECRUITING, TODAY);
        Posting dueLater = save(PostingStatus.RECRUITING, TODAY.plusDays(3));
        Posting noDeadline = save(PostingStatus.RECRUITING, null);
        Posting staleRecruiting = save(PostingStatus.RECRUITING, TODAY.minusDays(1));

        CursorSearchResult result = search(null, APPLY_DEADLINE_ASC);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(
                        dueToday.getId(),
                        dueLater.getId(),
                        noDeadline.getId(),
                        staleRecruiting.getId());
    }

    @Test
    void search_treatsDeadlineOfTodayAsStillOpen() {
        Posting dueToday = save(PostingStatus.RECRUITING, TODAY);
        Posting staleYesterday = save(PostingStatus.RECRUITING, TODAY.minusDays(1));

        CursorSearchResult result = search(PostingStatus.RECRUITING, APPLY_DEADLINE_ASC);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(dueToday.getId(), staleYesterday.getId());
    }

    @Test
    void search_appliesNullDeadlinePriorityButNotStaleBucketPriority_whenStatusFilterIsClosed() {
        Posting closedNear = save(PostingStatus.CLOSED, TODAY.minusDays(1));
        Posting closedFar = save(PostingStatus.CLOSED, TODAY.minusDays(5));
        Posting closedNoDeadline = save(PostingStatus.CLOSED, null);

        CursorSearchResult result = search(PostingStatus.CLOSED, APPLY_DEADLINE_ASC);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(closedFar.getId(), closedNear.getId(), closedNoDeadline.getId());
    }

    @Test
    void search_ordersByAppliedCountDescending_whenSortIsAppliedCount() {
        Posting fewApplicants = save(PostingStatus.RECRUITING, TODAY.plusDays(1), 1);
        Posting mostApplicants = save(PostingStatus.RECRUITING, TODAY.plusDays(1), 10);
        Posting noApplicants = save(PostingStatus.RECRUITING, TODAY.plusDays(1), 0);
        Sort appliedCountDescending =
                Sort.by(Sort.Direction.DESC, "appliedCount").and(Sort.by(Sort.Direction.DESC, "id"));

        CursorSearchResult result = search(PostingStatus.RECRUITING, appliedCountDescending);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(
                        mostApplicants.getId(), fewApplicants.getId(), noApplicants.getId());
    }

    @Test
    void search_ordersById_whenSortingByIdAscending() {
        Posting first = save(PostingStatus.RECRUITING, TODAY.plusDays(1));
        Posting second = save(PostingStatus.RECRUITING, TODAY.plusDays(1));
        Posting third = save(PostingStatus.RECRUITING, TODAY.plusDays(1));

        CursorSearchResult result = search(PostingStatus.RECRUITING, Sort.by(Sort.Direction.ASC, "id"));

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(first.getId(), second.getId(), third.getId());
    }

    @Test
    void search_ordersByCreatedAt_whenNoSortSpecified() {
        Posting first = save(PostingStatus.RECRUITING, TODAY.plusDays(1));
        Posting second = save(PostingStatus.RECRUITING, TODAY.plusDays(1));

        CursorSearchResult result = search(PostingStatus.RECRUITING, Sort.unsorted());

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(second.getId(), first.getId());
    }

    /**
     * 모집공고(external=true)를 실제로 insert해 신청 가능한 모집공고가 봉사공고와 함께 apply_deadline_at 오름차순으로 올바르게
     * 정렬되는지 확인한다(#163 리뷰 M2 — MEETING_RECRUIT UNION 분기를 실 DB로 검증하는 유일한 테스트).
     */
    @Test
    void
    search_ordersMeetingRecruitRowsAlongsidePostingRows_whenSortingByApplyDeadlineAtAscending() {
        Post nearRecruit = saveRecruit(TODAY.atStartOfDay().plusDays(1));
        Post farRecruit = saveRecruit(TODAY.atStartOfDay().plusDays(5));
        Posting openPosting = save(PostingStatus.RECRUITING, TODAY.plusDays(10));

        CursorSearchResult result = search(PostingStatus.RECRUITING, APPLY_DEADLINE_ASC);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(nearRecruit.getId(), farRecruit.getId(), openPosting.getId());
    }

    @Test
    void search_appliesActivityDateOverlapFilter_forPostingSource() {
        Posting withinRange = savePostingWithActivityDates(TODAY.plusDays(5), TODAY.plusDays(10));
        Posting overlapsRangeStart =
                savePostingWithActivityDates(TODAY.plusDays(1), TODAY.plusDays(6));
        savePostingWithActivityDates(TODAY.minusDays(10), TODAY.minusDays(5));
        savePostingWithActivityDates(TODAY.plusDays(20), TODAY.plusDays(25));

        CursorSearchResult result =
                unifiedPostingQueryRepository.search(
                        null,
                        null,
                        null,
                        null,
                        TODAY.plusDays(5),
                        TODAY.plusDays(10),
                        null,
                        null,
                        ID_DESC,
                        null,
                        20);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactlyInAnyOrder(withinRange.getId(), overlapsRangeStart.getId());
    }

    @Test
    void search_appliesActivityDateOverlapFilter_forMeetingRecruitSource() {
        Post withinRange = saveRecruit(TODAY.plusDays(3).atStartOfDay());
        saveRecruit(TODAY.minusDays(12).atStartOfDay());
        saveRecruit(TODAY.plusDays(18).atStartOfDay());

        CursorSearchResult result =
                unifiedPostingQueryRepository.search(
                        null,
                        null,
                        null,
                        null,
                        TODAY.plusDays(5),
                        TODAY.plusDays(10),
                        null,
                        null,
                        ID_DESC,
                        null,
                        20);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(withinRange.getId());
    }

    // ── 키셋(커서) 페이지네이션 자체 검증 ──

    @Test
    void search_setsHasNextTrue_whenMoreRowsExistBeyondRequestedSize() {
        save(PostingStatus.RECRUITING, TODAY.plusDays(1));
        save(PostingStatus.RECRUITING, TODAY.plusDays(1));
        save(PostingStatus.RECRUITING, TODAY.plusDays(1));

        CursorSearchResult result =
                unifiedPostingQueryRepository.search(
                        PostingStatus.RECRUITING, null, null, null, null, null, null, null,
                        ID_DESC, null, 2);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotBlank();
    }

    @Test
    void search_setsHasNextFalse_whenAllRowsFitWithinRequestedSize() {
        save(PostingStatus.RECRUITING, TODAY.plusDays(1));
        save(PostingStatus.RECRUITING, TODAY.plusDays(1));

        CursorSearchResult result =
                unifiedPostingQueryRepository.search(
                        PostingStatus.RECRUITING, null, null, null, null, null, null, null,
                        ID_DESC, null, 20);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void search_paginatesThroughAllRows_withoutDuplicatesOrGaps_whenFollowingCursor() {
        List<Long> expectedIds = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            expectedIds.add(save(PostingStatus.RECRUITING, TODAY.plusDays(1)).getId());
        }
        // ID_DESC이므로 기대 순서는 삽입 역순.
        List<Long> expectedOrder = new ArrayList<>(expectedIds);
        java.util.Collections.reverse(expectedOrder);

        List<Long> collected = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;
        while (hasNext) {
            CursorSearchResult page =
                    unifiedPostingQueryRepository.search(
                            PostingStatus.RECRUITING, null, null, null, null, null, null, null,
                            ID_DESC, cursor, 3);
            page.rows().forEach(row -> collected.add(row.id()));
            cursor = page.nextCursor();
            hasNext = page.hasNext();
        }

        assertThat(collected).containsExactlyElementsOf(expectedOrder);
    }

    @Test
    void
    search_paginatesThroughAllRows_withoutDuplicatesOrGaps_whenFollowingCursor_forApplyDeadlineAscending() {
        Posting a = save(PostingStatus.RECRUITING, TODAY.plusDays(1));
        Posting b = save(PostingStatus.RECRUITING, TODAY.plusDays(2));
        Posting c = save(PostingStatus.RECRUITING, null);
        Posting d = save(PostingStatus.RECRUITING, TODAY.minusDays(1));

        List<Long> collected = new ArrayList<>();
        String cursor = null;
        boolean hasNext = true;
        while (hasNext) {
            CursorSearchResult page = search(null, APPLY_DEADLINE_ASC, cursor, 2);
            page.rows().forEach(row -> collected.add(row.id()));
            cursor = page.nextCursor();
            hasNext = page.hasNext();
        }

        assertThat(collected).containsExactly(a.getId(), b.getId(), c.getId(), d.getId());
    }

    @Test
    void search_throwsValidationError_whenCursorIsNotValidBase64() {
        save(PostingStatus.RECRUITING, TODAY.plusDays(1));

        assertThatThrownBy(
                () ->
                        unifiedPostingQueryRepository.search(
                                PostingStatus.RECRUITING,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                ID_DESC,
                                "이건-유효한-base64가-아님!!",
                                20))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void search_throwsValidationError_whenCursorKeyCountDoesNotMatchCurrentSort() {
        // id 정렬(키 1개: id)로 발급된 커서를 appliedCount 정렬(키 2개: appliedCount, id)에 재사용하는 경우.
        // size=1인데 2건을 만들어야 hasNext=true가 되어 nextCursor가 null이 아닌 실제 값으로 나온다.
        save(PostingStatus.RECRUITING, TODAY.plusDays(1));
        save(PostingStatus.RECRUITING, TODAY.plusDays(1));
        CursorSearchResult idPage =
                unifiedPostingQueryRepository.search(
                        PostingStatus.RECRUITING, null, null, null, null, null, null, null,
                        ID_DESC, null, 1);
        String cursorFromIdSort = idPage.nextCursor();

        assertThatThrownBy(
                () ->
                        unifiedPostingQueryRepository.search(
                                PostingStatus.RECRUITING,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Sort.by(Sort.Direction.DESC, "appliedCount")
                                        .and(Sort.by(Sort.Direction.DESC, "id")),
                                cursorFromIdSort,
                                20))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // ── 헬퍼 ──

    private CursorSearchResult search(PostingStatus status, Sort sort) {
        return search(status, sort, null, 20);
    }

    private CursorSearchResult search(PostingStatus status, Sort sort, String cursor, int size) {
        return unifiedPostingQueryRepository.search(
                status, null, null, null, null, null, null, null, sort, cursor, size);
    }

    private Posting save(PostingStatus status, LocalDate noticeEnd) {
        return save(status, noticeEnd, null);
    }

    /** 활동일 겹침(overlap) 필터 테스트 전용 — act_start_date/act_end_date를 직접 지정한다. */
    private Posting savePostingWithActivityDates(LocalDate actStartDate, LocalDate actEndDate) {
        return postingRepository.save(
                Posting.builder()
                        .title("활동일 필터 테스트 공고")
                        .status(PostingStatus.RECRUITING)
                        .activityDate(actStartDate)
                        .actStartDate(actStartDate)
                        .actEndDate(actEndDate)
                        .category(PostingCategory.ENVIRONMENT)
                        .source(PostingSource.API_1365)
                        .build());
    }

    private Posting save(PostingStatus status, LocalDate noticeEnd, Integer applicantCount) {
        return postingRepository.save(
                Posting.builder()
                        .title("테스트 공고")
                        .status(status)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .noticeEndDate(noticeEnd)
                        .applicantCount(applicantCount)
                        .category(PostingCategory.ENVIRONMENT)
                        .source(PostingSource.API_1365)
                        .build());
    }

    /** 신청 가능한(external=true, 미확정, 정원 미달) 모집공고 1건을 실제로 insert하고 연결된 게시글({@code post})을 반환한다. */
    private Post saveRecruit(LocalDateTime applyDeadlineAt) {
        Region region =
                regionRepository.save(
                        Region.create(
                                "모집공고 테스트구", 2, "996" + (System.nanoTime() % 10_000_000L), null));
        User host = userRepository.save(user(region));
        Meeting meeting =
                meetingRepository.save(
                        Meeting.create(
                                "모집공고 테스트 모임",
                                "통합 목록 정렬 테스트",
                                10,
                                applyDeadlineAt.plusDays(1),
                                null,
                                Set.of(PostingCategory.ENVIRONMENT),
                                region.getId(),
                                host,
                                null,
                                null,
                                null,
                                null));
        Post post =
                postRepository.save(
                        Post.create(meeting, host, "모집공고 테스트 게시글", "내용", PostType.RECRUIT, 10));
        meetingRecruitRepository.save(
                MeetingRecruit.create(
                        post.getId(),
                        region.getId(),
                        "테스트 장소",
                        applyDeadlineAt.plusDays(2),
                        applyDeadlineAt.plusDays(2).plusHours(3),
                        10,
                        false,
                        null,
                        applyDeadlineAt,
                        true,
                        Set.of(PostingCategory.ENVIRONMENT),
                        null));
        return post;
    }

    private User user(Region region) {
        String suffix = String.valueOf(System.nanoTime());
        String uniqueSuffix = suffix.substring(Math.max(0, suffix.length() - 8));
        return User.create(
                "repo-user",
                LocalDate.of(1995, 1, 1),
                Gender.MALE,
                "010" + uniqueSuffix,
                null,
                null,
                "repo-" + uniqueSuffix,
                null,
                true,
                true,
                false,
                region,
                java.util.List.of());
    }
}