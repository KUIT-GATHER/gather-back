package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingSource;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository.SearchResult;
import java.time.LocalDate;
import java.time.ZoneId;
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
                        null, null, null, null, null, null, applyDeadlineAscending);

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
                        null, null, null, null, null, null, applyDeadlineAscending);

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
                        applyDeadlineAscending);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(dueToday.getId(), staleYesterday.getId());
    }

    @Test
    void search_doesNotApplyPriority_whenStatusFilterIsClosed() {
        Posting closedNear = save(PostingStatus.CLOSED, TODAY.minusDays(1));
        Posting closedFar = save(PostingStatus.CLOSED, TODAY.minusDays(5));
        Pageable applyDeadlineAscending =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applyDeadlineAt"));

        SearchResult result =
                unifiedPostingQueryRepository.search(
                        PostingStatus.CLOSED, null, null, null, null, null, applyDeadlineAscending);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(closedFar.getId(), closedNear.getId());
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
                        appliedCountDescending);

        assertThat(result.rows())
                .extracting(UnifiedPostingRow::id)
                .containsExactly(
                        mostApplicants.getId(), fewApplicants.getId(), noApplicants.getId());
    }

    private Posting save(PostingStatus status, LocalDate noticeEnd) {
        return save(status, noticeEnd, null);
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
}
