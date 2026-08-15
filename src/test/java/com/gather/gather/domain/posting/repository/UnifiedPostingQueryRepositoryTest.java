package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository.SearchResult;
import com.gather.gather.domain.recruit.entity.MeetingRecruit;
import com.gather.gather.domain.recruit.repository.MeetingRecruitRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UnifiedPostingQueryRepository#search}의 마감임박(applyDeadlineAt 오름차순) 정렬 보정 검증. 외부 공공데이터 API
 * 동기화 지연으로 noticeEndDate가 이미 지났는데도 status가 아직 RECRUITING으로 남아있는 봉사공고가 마감임박 정렬 최상단에 노출되던 버그의 회귀
 * 테스트다({@link PostingRepositoryTest}의 동일 시나리오를 통합 쿼리 기준으로 재현).
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

    @Test
    void search_pushesStaleRecruitingBehindOpenRecruiting_whenSortingByApplyDeadlineAtAscending() {
        Posting staleRecruiting = save(PostingStatus.RECRUITING, TODAY.minusDays(1));
        Posting openRecruiting = save(PostingStatus.RECRUITING, TODAY.plusDays(5));
        Posting closed = save(PostingStatus.CLOSED, TODAY.minusDays(2));
        Pageable applyDeadlineAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applyDeadlineAt"));

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        null, null, null, null, null, null, null, null, applyDeadlineAscending);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(openRecruiting.getId(), closed.getId(), staleRecruiting.getId());
    }

    @Test
    void search_ordersNullDeadlineAfterOpenButBeforeStale_whenSortingByApplyDeadlineAtAscending() {
        // 마감일이 없는 상시모집 공고는 DB의 NULL 정렬 정책상 오름차순 정렬에서 최상단에 노출될 수 있으므로,
        // 마감일이 있고 아직 지나지 않은 공고 다음, 이미 마감된 공고보다는 앞에 오도록 강제한다.
        Posting dueToday = save(PostingStatus.RECRUITING, TODAY);
        Posting dueLater = save(PostingStatus.RECRUITING, TODAY.plusDays(3));
        Posting noDeadline = save(PostingStatus.RECRUITING, null);
        Posting staleRecruiting = save(PostingStatus.RECRUITING, TODAY.minusDays(1));
        Pageable applyDeadlineAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applyDeadlineAt"));

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        null, null, null, null, null, null, null, null, applyDeadlineAscending);

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
        Pageable applyDeadlineAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applyDeadlineAt"));

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        PostingStatus.RECRUITING,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        applyDeadlineAscending);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(dueToday.getId(), staleYesterday.getId());
    }

    /**
     * status=CLOSED일 때는 stale-RECRUITING 우선순위 보정(버킷 CASE)이 붙지 않아 순수 apply_deadline_at 오름차순만 적용되지만,
     * 마감일 없는 공고를 뒤로 미루는 보정은 status와 무관하게 항상 붙는다(#163 리뷰 M3/M4 — closedNoDeadline이 없으면 이 테스트는
     * buildOrderBy의 보정을 되돌리거나 status 가드를 지워도 통과하는 무판별 테스트였다).
     */
    @Test
    void search_appliesNullDeadlinePriorityButNotStaleBucketPriority_whenStatusFilterIsClosed() {
        Posting closedNear = save(PostingStatus.CLOSED, TODAY.minusDays(1));
        Posting closedFar = save(PostingStatus.CLOSED, TODAY.minusDays(5));
        Posting closedNoDeadline = save(PostingStatus.CLOSED, null);
        Pageable applyDeadlineAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applyDeadlineAt"));

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        PostingStatus.CLOSED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        applyDeadlineAscending);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(closedFar.getId(), closedNear.getId(), closedNoDeadline.getId());
    }

    @Test
    void search_ordersByAppliedCountDescending_whenSortIsAppliedCount() {
        Posting fewApplicants = save(PostingStatus.RECRUITING, TODAY.plusDays(1), 1);
        Posting mostApplicants = save(PostingStatus.RECRUITING, TODAY.plusDays(1), 10);
        Posting noApplicants = save(PostingStatus.RECRUITING, TODAY.plusDays(1), 0);
        Pageable appliedCountDescending =
                PageRequest.of(
                        0,
                        20,
                        Sort.by(Sort.Direction.DESC, "appliedCount")
                                .and(Sort.by(Sort.Direction.DESC, "id")));

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        PostingStatus.RECRUITING,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        appliedCountDescending);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(
                        mostApplicants.getId(), fewApplicants.getId(), noApplicants.getId());
    }

    /**
     * MEETING_RECRUIT UNION 분기를 실 DB로 검증하는 유일한 테스트(#163 리뷰 M2 — 이전에는 volunteer_posting 픽스처만 있어 이
     * 분기가 리포 전체에서 0건 커버됐다). 모집공고(external=true)를 실제로 insert해 신청 가능한 모집공고가 봉사공고와 함께
     * apply_deadline_at 오름차순으로 올바르게 정렬되는지 확인한다.
     */
    @Test
    void
            search_ordersMeetingRecruitRowsAlongsidePostingRows_whenSortingByApplyDeadlineAtAscending() {
        Post nearRecruit = saveRecruit(TODAY.atStartOfDay().plusDays(1));
        Post farRecruit = saveRecruit(TODAY.atStartOfDay().plusDays(5));
        Posting openPosting = save(PostingStatus.RECRUITING, TODAY.plusDays(10));
        Pageable applyDeadlineAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applyDeadlineAt"));

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        PostingStatus.RECRUITING,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        applyDeadlineAscending);

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
        Pageable pageable = PageRequest.of(0, 20);

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        null,
                        null,
                        null,
                        null,
                        TODAY.plusDays(5),
                        TODAY.plusDays(10),
                        null,
                        null,
                        pageable);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactlyInAnyOrder(withinRange.getId(), overlapsRangeStart.getId());
    }

    @Test
    void search_appliesActivityDateOverlapFilter_forMeetingRecruitSource() {
        Post withinRange = saveRecruit(TODAY.plusDays(3).atStartOfDay());
        saveRecruit(TODAY.minusDays(12).atStartOfDay());
        saveRecruit(TODAY.plusDays(18).atStartOfDay());
        Pageable pageable = PageRequest.of(0, 20);

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        null,
                        null,
                        null,
                        null,
                        TODAY.plusDays(5),
                        TODAY.plusDays(10),
                        null,
                        null,
                        pageable);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(withinRange.getId());
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
