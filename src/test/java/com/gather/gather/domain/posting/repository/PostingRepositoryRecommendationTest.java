package com.gather.gather.domain.posting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingSource;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PostingRepository#searchRecommendationCandidates}의 실제 DB 동작 검증(V67 생성 컬럼·전용 복합 인덱스 대상).
 * status=RECRUITING, is_active=true, 마감일이 지나지 않은(또는 상시모집인) 공고만 후보로 남는지, 마감임박 오름차순(상시모집은 맨 뒤) 정렬, 지역
 * 필터, {@link org.springframework.data.domain.Slice}의 hasNext 판단이 페이지 경계에서 올바른지가 핵심 검증 대상이다. 범용
 * 조회({@link PostingRepository#search})와는 필터·정렬 규약이 달라 {@link PostingRepositoryTest}와 별도 파일로 분리한다.
 */
@SpringBootTest
@Transactional
class PostingRepositoryRecommendationTest {

    @Autowired private PostingRepository postingRepository;

    private static final Pageable RECOMMEND_PAGEABLE = PageRequest.of(0, 200);

    /**
     * PostingRepositoryImpl이 마감 여부 판정에 Asia/Seoul 기준 오늘 날짜를 쓰므로, 테스트의 "오늘" 기준도 동일한 시간대로 맞춰야 CI
     * 서버(UTC)와 자정 경계에서 날짜가 어긋나 간헐적으로 실패하는 일을 막을 수 있다.
     */
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Test
    void searchRecommendationCandidates_excludesInactivePostings() {
        Posting active =
                recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(3), null);
        recommendationCandidate(PostingStatus.RECRUITING, false, TODAY.plusDays(3), null);

        var result =
                postingRepository.searchRecommendationCandidates(null, TODAY, RECOMMEND_PAGEABLE);

        assertThat(result.getContent()).extracting(Posting::getId).containsExactly(active.getId());
    }

    @Test
    void searchRecommendationCandidates_excludesClosedStatus() {
        Posting recruiting =
                recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(3), null);
        recommendationCandidate(PostingStatus.CLOSED, true, TODAY.plusDays(3), null);

        var result =
                postingRepository.searchRecommendationCandidates(null, TODAY, RECOMMEND_PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(recruiting.getId());
    }

    @Test
    void
            searchRecommendationCandidates_excludesPostingsPastNoticeEndDate_evenWhenStatusIsStillRecruiting() {
        // 외부 공공데이터 API 동기화 지연으로 마감일이 지났는데도 status가 아직 RECRUITING인 시나리오.
        Posting stillOpen = recommendationCandidate(PostingStatus.RECRUITING, true, TODAY, null);
        recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.minusDays(1), null);

        var result =
                postingRepository.searchRecommendationCandidates(null, TODAY, RECOMMEND_PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(stillOpen.getId());
    }

    @Test
    void searchRecommendationCandidates_ordersByNoticeEndDateAscendingWithNullDatesLast() {
        Posting dueLater =
                recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(5), null);
        Posting dueSoon =
                recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(1), null);
        Posting rolling = recommendationCandidate(PostingStatus.RECRUITING, true, null, null);

        var result =
                postingRepository.searchRecommendationCandidates(null, TODAY, RECOMMEND_PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(dueSoon.getId(), dueLater.getId(), rolling.getId());
    }

    @Test
    void searchRecommendationCandidates_filtersByRegionIds_whenProvided() {
        Posting matching =
                recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(1), 42L);
        recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(1), 43L);

        var result =
                postingRepository.searchRecommendationCandidates(
                        List.of(42L), TODAY, RECOMMEND_PAGEABLE);

        assertThat(result.getContent())
                .extracting(Posting::getId)
                .containsExactly(matching.getId());
    }

    @Test
    void searchRecommendationCandidates_returnsEmpty_withoutException_whenRegionIdsIsEmptyList() {
        recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(1), 42L);

        var result =
                postingRepository.searchRecommendationCandidates(
                        List.of(), TODAY, RECOMMEND_PAGEABLE);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchRecommendationCandidates_hasNext_isTrue_whenMoreCandidatesRemain() {
        recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(1), null);
        recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(2), null);

        var result =
                postingRepository.searchRecommendationCandidates(null, TODAY, PageRequest.of(0, 1));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void searchRecommendationCandidates_hasNext_isFalse_onLastPage() {
        recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(1), null);

        var result =
                postingRepository.searchRecommendationCandidates(null, TODAY, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void searchRecommendationCandidates_hasNext_isFalse_whenContentExactlyFillsPage() {
        // pageSize+1건을 조회해 hasNext를 판단하는 구현이므로, 후보 수가 정확히 pageSize와 같은 경계에서
        // 남는 후보가 없는데도 hasNext가 true로 잘못 판정되지 않는지 확인한다(off-by-one 회귀 방지).
        recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(1), null);
        recommendationCandidate(PostingStatus.RECRUITING, true, TODAY.plusDays(2), null);

        var result =
                postingRepository.searchRecommendationCandidates(null, TODAY, PageRequest.of(0, 2));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
    }

    private Posting recommendationCandidate(
            PostingStatus status, boolean isActive, LocalDate noticeEndDate, Long regionId) {
        return postingRepository.save(
                Posting.builder()
                        .title("추천 후보 테스트 공고")
                        .status(status)
                        .activityDate(LocalDate.of(2026, 7, 15))
                        .noticeEndDate(noticeEndDate)
                        .isActive(isActive)
                        .regionId(regionId)
                        .category(PostingCategory.ENVIRONMENT)
                        .source(PostingSource.API_1365)
                        .build());
    }
}
