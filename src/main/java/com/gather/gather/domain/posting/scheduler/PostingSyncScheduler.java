package com.gather.gather.domain.posting.scheduler;

import com.gather.gather.domain.posting.service.PostingSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostingSyncScheduler {

    private final PostingSyncService postingSyncService;

    /** 매일 새벽 3시 1회 실행. */
    @Scheduled(cron = "0 0 3 * * *")
    public void syncPostings() {
        try {
            postingSyncService.syncRecentPostings();
        } catch (RuntimeException e) {
            log.error("봉사공고 동기화 배치 실행 중 예외 발생", e);
        }
    }
}
