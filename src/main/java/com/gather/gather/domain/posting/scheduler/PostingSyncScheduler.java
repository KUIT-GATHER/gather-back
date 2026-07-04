package com.gather.gather.domain.posting.scheduler;

import com.gather.gather.domain.posting.service.PostingSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "posting.sync",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PostingSyncScheduler {

    private final PostingSyncService postingSyncService;

    /** 매일 새벽 3시(KST) 1회 실행. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void syncPostings() {
        try {
            postingSyncService.syncRecentPostings();
        } catch (RuntimeException e) {
            log.error("봉사공고 동기화 배치 실행 중 예외 발생", e);
        }
    }
}
