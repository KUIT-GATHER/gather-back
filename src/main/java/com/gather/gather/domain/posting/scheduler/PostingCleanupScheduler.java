package com.gather.gather.domain.posting.scheduler;

import com.gather.gather.domain.posting.service.PostingLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "posting.cleanup",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PostingCleanupScheduler {

    private final PostingLifecycleService postingLifecycleService;

    /** 매일 새벽 4시(KST) 1회 실행. 활동 종료일이 지난 공고를 비활성화한다. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void cleanupPostings() {
        try {
            int count = postingLifecycleService.deactivateExpiredPostings();
            log.info("만료 공고 비활성화 완료. count={}", count);
        } catch (RuntimeException e) {
            log.error("만료 공고 비활성화 배치 실패", e);
        }
    }
}
