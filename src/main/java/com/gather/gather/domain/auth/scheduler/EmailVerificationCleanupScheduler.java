package com.gather.gather.domain.auth.scheduler;

import com.gather.gather.domain.auth.service.EmailVerificationCleanupService;
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
@ConditionalOnProperty(
        prefix = "gather.auth.email-verification",
        name = "cleanup-scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EmailVerificationCleanupScheduler {

    private final EmailVerificationCleanupService cleanupService;

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupAfterStartup() {
        cleanup();
    }

    @Scheduled(
            fixedDelayString = "${gather.auth.email-verification.cleanup-fixed-delay:1h}",
            initialDelayString = "${gather.auth.email-verification.cleanup-initial-delay:1h}")
    public void cleanupHourly() {
        cleanup();
    }

    private void cleanup() {
        purgeLegacy();
        purgeOverdue();
    }

    // 기동 시점 관문을 통과한 뒤에도 남을 수 있는 평문 행을 주기적으로 걷어낸다.
    // 보관 기간 정리와 서로 막지 않도록 실패를 각각 흡수하고 다음 주기에 다시 시도한다.
    private void purgeLegacy() {
        try {
            int deletedCount = cleanupService.purgeLegacyVerifications();
            log.info("Email verification legacy purge completed: deletedCount={}", deletedCount);
        } catch (RuntimeException exception) {
            log.error("Email verification legacy purge failed", exception);
        }
    }

    private void purgeOverdue() {
        try {
            int deletedCount = cleanupService.cleanupOverdueVerifications();
            log.info("Email verification cleanup completed: deletedCount={}", deletedCount);
        } catch (RuntimeException exception) {
            log.error("Email verification cleanup failed", exception);
        }
    }
}
