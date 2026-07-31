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

    /** 매일 새벽 4시 10분(KST) 1회 실행. 비활성화된 지 1개월 이상 지난 공고의 content를 비운다. */
    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    public void clearExpiredPostingContent() {
        try {
            int count = postingLifecycleService.clearExpiredPostingContent();
            log.info("만료 공고 content 삭제 완료. count={}", count);
        } catch (RuntimeException e) {
            log.error("만료 공고 content 삭제 배치 실패", e);
        }
    }
}
