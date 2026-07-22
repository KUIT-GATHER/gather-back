package com.gather.gather.domain.meeting.scheduler;

import com.gather.gather.domain.meeting.service.MeetingKeywordRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "meeting.keyword",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MeetingKeywordAggregationScheduler {

    private final MeetingKeywordRecommendationService meetingKeywordRecommendationService;

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void aggregateKeywords() {
        try {
            int count = meetingKeywordRecommendationService.aggregate();
            log.info("모임 추천검색어 집계 배치 성공. count={}", count);
        } catch (RuntimeException e) {
            log.error("모임 추천검색어 집계 배치 실패", e);
        }

        try {
            meetingKeywordRecommendationService.cleanupOldLogs();
        } catch (RuntimeException e) {
            log.error("모임 검색 로그 정리 배치 실패", e);
        }
    }
}
