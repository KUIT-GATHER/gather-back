package com.gather.gather.domain.auth.scheduler;

import com.gather.gather.domain.auth.service.UnlinkRetrySummary;
import com.gather.gather.domain.auth.service.WithdrawnAccountCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "gather.auth.withdrawal",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WithdrawnAccountScheduler {

    private final WithdrawnAccountCleanupService withdrawnAccountCleanupService;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanupWithdrawnAccounts() {
        anonymizeExpiredAccounts();
        retryPendingUnlinks();
    }

    private void anonymizeExpiredAccounts() {
        try {
            int count = withdrawnAccountCleanupService.anonymizeExpiredAccounts();
            if (count > 0) {
                log.info("Withdrawn accounts anonymized. count={}", count);
            }
        } catch (RuntimeException exception) {
            log.error("Withdrawn-account anonymization batch failed.", exception);
        }
    }

    private void retryPendingUnlinks() {
        try {
            UnlinkRetrySummary summary = withdrawnAccountCleanupService.retryPendingUnlinks();
            if (summary.attemptedCount() > 0) {
                log.info(
                        "Kakao unlink retry finished. attempted={}, resolved={}, noLinkedAccount={}, retryPending={}, failed={}, forcedDeletion={}",
                        summary.attemptedCount(),
                        summary.resolvedCount(),
                        summary.noLinkedAccountCount(),
                        summary.retryPendingCount(),
                        summary.failedCount(),
                        summary.forcedDeletionCount());
            }
        } catch (RuntimeException exception) {
            log.error("Kakao unlink retry batch failed.", exception);
        }
    }
}
