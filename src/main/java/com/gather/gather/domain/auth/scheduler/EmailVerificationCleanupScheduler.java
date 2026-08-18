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
        try {
            int deletedCount = cleanupService.cleanupOverdueVerifications();
            log.info("Email verification cleanup completed: deletedCount={}", deletedCount);
        } catch (RuntimeException exception) {
            log.error("Email verification cleanup failed", exception);
        }
    }
}
