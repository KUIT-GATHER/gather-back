package com.gather.gather.domain.posting.scheduler;

import com.gather.gather.domain.posting.service.PostingKeywordRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "posting.keyword",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PostingKeywordAggregationScheduler {

    private final PostingKeywordRecommendationService postingKeywordRecommendationService;

    /**
     * 매일 새벽 5시(KST) 1회 실행. 최근 60일 검색어를 집계해 추천검색어 상위 10개를 갱신하고, 60일 초과 로그를 정리한다. 집계와 정리를 각각 별도로 감싸서,
     * 집계가 실패해도 로그 정리는 계속 진행되게 한다(그렇지 않으면 실패가 반복될수록 다음 집계에서 처리할 로그가 계속 쌓이는 자기증폭 상태가 될 수 있다).
     */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void aggregateKeywords() {
        try {
            int count = postingKeywordRecommendationService.aggregate();
            log.info("추천검색어 집계 배치 성공. count={}", count);
        } catch (RuntimeException e) {
            log.error("추천검색어 집계 배치 실패", e);
        }

        try {
            postingKeywordRecommendationService.cleanupOldLogs();
        } catch (RuntimeException e) {
            log.error("검색 로그 정리 배치 실패", e);
        }
    }
}
