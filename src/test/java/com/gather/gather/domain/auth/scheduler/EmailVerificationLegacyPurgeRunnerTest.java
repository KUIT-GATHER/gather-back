package com.gather.gather.domain.auth.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.service.EmailVerificationCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationLegacyPurgeRunnerTest {

    @Mock private EmailVerificationCleanupService cleanupService;

    private EmailVerificationLegacyPurgeRunner runner;

    @BeforeEach
    void setUp() {
        runner = new EmailVerificationLegacyPurgeRunner(cleanupService);
    }

    @Test
    @DisplayName("기동 시점에 평문 행 파기를 실행한다")
    void run_purgesLegacyRows() {
        when(cleanupService.purgeLegacyVerifications()).thenReturn(2);

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();

        verify(cleanupService).purgeLegacyVerifications();
    }

    @Test
    @DisplayName("파기에 실패하면 예외를 삼키지 않고 기동을 실패시킨다")
    void run_purgeFailure_propagatesExceptionToFailStartup() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(cleanupService)
                .purgeLegacyVerifications();

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }
}
