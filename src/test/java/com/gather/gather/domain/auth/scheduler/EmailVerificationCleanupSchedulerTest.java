package com.gather.gather.domain.auth.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.service.EmailVerificationCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationCleanupSchedulerTest {

    @Mock private EmailVerificationCleanupService cleanupService;

    private EmailVerificationCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EmailVerificationCleanupScheduler(cleanupService);
    }

    @Test
    void startupAndHourlyCleanup_useSameIdempotentService() {
        when(cleanupService.cleanupOverdueVerifications()).thenReturn(3, 0);

        scheduler.cleanupAfterStartup();
        scheduler.cleanupHourly();

        verify(cleanupService, times(2)).cleanupOverdueVerifications();
    }

    @Test
    void cleanupFailure_doesNotStopFutureRuns() {
        doThrow(new IllegalStateException("database unavailable"))
                .doReturn(0)
                .when(cleanupService)
                .cleanupOverdueVerifications();

        scheduler.cleanupHourly();
        scheduler.cleanupHourly();

        verify(cleanupService, times(2)).cleanupOverdueVerifications();
    }
}
