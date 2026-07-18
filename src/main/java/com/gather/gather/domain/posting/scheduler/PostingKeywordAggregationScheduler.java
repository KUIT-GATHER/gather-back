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

    /** 매일 새벽 5시(KST) 1회 실행. 최근 60일 검색어를 집계해 추천검색어 상위 10개를 갱신하고, 60일 초과 로그를 정리한다. */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void aggregateKeywords() {
        try {
            int count = postingKeywordRecommendationService.aggregate();
            postingKeywordRecommendationService.cleanupOldLogs();
            log.info("추천검색어 집계 배치 성공. count={}", count);
        } catch (RuntimeException e) {
            log.error("추천검색어 집계 배치 실패", e);
        }
    }
}
