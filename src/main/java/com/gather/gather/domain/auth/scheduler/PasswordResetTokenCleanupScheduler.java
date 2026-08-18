package com.gather.gather.domain.auth.scheduler;

import com.gather.gather.domain.auth.service.PasswordResetTokenCleanupService;
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
        prefix = "gather.auth.password-reset",
        name = "cleanup-scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PasswordResetTokenCleanupScheduler {

    private final PasswordResetTokenCleanupService cleanupService;

    /** 재기동 중 만료된 토큰이 다음 주기까지 남지 않도록 기동 직후 한 번 파기한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupAfterStartup() {
        cleanup();
    }

    @Scheduled(
            fixedDelayString = "${gather.auth.password-reset.cleanup-fixed-delay:5m}",
            initialDelayString = "${gather.auth.password-reset.cleanup-initial-delay:5m}")
    public void cleanupExpiredTokens() {
        cleanup();
    }

    private void cleanup() {
        try {
            int deletedCount = cleanupService.cleanupExpiredTokens();
            if (deletedCount > 0) {
                log.info("Password reset token cleanup completed: deletedCount={}", deletedCount);
            } else {
                log.debug("Password reset token cleanup completed: deletedCount=0");
            }
        } catch (RuntimeException exception) {
            log.error("Password reset token cleanup failed", exception);
        }
    }
}
