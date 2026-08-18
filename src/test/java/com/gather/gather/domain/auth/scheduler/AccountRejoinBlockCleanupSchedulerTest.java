package com.gather.gather.domain.auth.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.service.AccountRejoinBlockCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountRejoinBlockCleanupSchedulerTest {

    @Mock private AccountRejoinBlockCleanupService cleanupService;

    private AccountRejoinBlockCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AccountRejoinBlockCleanupScheduler(cleanupService);
    }

    @Test
    @DisplayName("기동 직후 파기와 주기 파기는 같은 멱등 서비스를 사용한다")
    void startupAndScheduledCleanup_useSameIdempotentService() {
        when(cleanupService.cleanupRetentionExpiredBlocks()).thenReturn(2, 0);

        scheduler.cleanupAfterStartup();
        scheduler.cleanupHourly();

        verify(cleanupService, times(2)).cleanupRetentionExpiredBlocks();
    }

    @Test
    @DisplayName("파기 실패는 예외를 전파하지 않고 다음 실행을 막지 않는다")
    void cleanupFailure_doesNotPropagateAndDoesNotStopFutureRuns() {
        doThrow(new IllegalStateException("database unavailable"))
                .doReturn(0)
                .when(cleanupService)
                .cleanupRetentionExpiredBlocks();

        assertThatCode(() -> scheduler.cleanupHourly()).doesNotThrowAnyException();
        assertThatCode(() -> scheduler.cleanupHourly()).doesNotThrowAnyException();

        verify(cleanupService, times(2)).cleanupRetentionExpiredBlocks();
    }
}
