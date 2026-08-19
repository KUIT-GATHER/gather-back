package com.gather.gather.domain.auth.scheduler;

import com.gather.gather.domain.auth.service.AccountRejoinBlockCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
// 과거 upsert가 source_user_id를 갱신하지 않아 legacy row의 기산점이 실제 탈퇴일보다 이를 수 있고 파기는 되돌릴 수
// 없으므로, 다른 cleanup scheduler와 달리 설정이 없으면 동작하지 않는 opt-in으로 둔다.
@ConditionalOnProperty(
        prefix = "gather.auth.rejoin-block",
        name = "cleanup-scheduler-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class AccountRejoinBlockCleanupScheduler {

    private final AccountRejoinBlockCleanupService cleanupService;

    /** 중단 기간이 길면 보관기간이 지난 block이 쌓이므로 기동 직후 한 번 밀린 분량을 파기한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupAfterStartup() {
        cleanup();
    }

    @Scheduled(
            fixedDelayString = "${gather.auth.rejoin-block.cleanup-fixed-delay:1h}",
            initialDelayString = "${gather.auth.rejoin-block.cleanup-initial-delay:1h}")
    public void cleanupHourly() {
        cleanup();
    }

    private void cleanup() {
        try {
            int deletedCount = cleanupService.cleanupRetentionExpiredBlocks();
            // 대부분의 실행은 0건이므로 실제 파기가 있었던 실행만 운영 로그로 남긴다.
            if (deletedCount > 0) {
                log.info("Account rejoin block cleanup completed: deletedCount={}", deletedCount);
            } else {
                log.debug("Account rejoin block cleanup completed: deletedCount=0");
            }
        } catch (RuntimeException exception) {
            log.error("Account rejoin block cleanup failed", exception);
        }
    }
}
