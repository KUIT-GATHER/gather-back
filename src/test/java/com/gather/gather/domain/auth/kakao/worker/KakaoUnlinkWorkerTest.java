package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KakaoUnlinkWorkerTest {

    @Mock private KakaoUnlinkClaimService claimService;
    @Mock private KakaoUnlinkTaskProcessor taskProcessor;

    private KakaoUnlinkWorker worker;

    @BeforeEach
    void setUp() {
        worker = new KakaoUnlinkWorker(claimService, taskProcessor);
    }

    @Test
    void configurationBlocked_stopsBeforeRemainingClaimedTask() {
        KakaoUnlinkClaim first = claim(1L);
        KakaoUnlinkClaim second = claim(2L);
        when(claimService.claimBatch()).thenReturn(List.of(first, second));
        when(taskProcessor.process(first))
                .thenReturn(KakaoUnlinkProcessingResult.CONFIGURATION_BLOCKED);

        worker.runBatch();

        verify(taskProcessor, never()).process(second);
    }

    @Test
    void nonBlockingResults_continueWithRemainingClaims() {
        KakaoUnlinkClaim first = claim(1L);
        KakaoUnlinkClaim second = claim(2L);
        when(claimService.claimBatch()).thenReturn(List.of(first, second));
        when(taskProcessor.process(first)).thenReturn(KakaoUnlinkProcessingResult.DEAD);
        when(taskProcessor.process(second)).thenReturn(KakaoUnlinkProcessingResult.STALE);

        worker.runBatch();

        verify(taskProcessor).process(first);
        verify(taskProcessor).process(second);
    }

    @Test
    void unexpectedTaskFailure_logsStackTraceWithoutClaimTokenAndContinues(CapturedOutput output) {
        KakaoUnlinkClaim first = claim(1L);
        KakaoUnlinkClaim second = claim(2L);
        when(claimService.claimBatch()).thenReturn(List.of(first, second));
        when(taskProcessor.process(first))
                .thenThrow(new IllegalStateException("unexpected-worker-failure"));
        when(taskProcessor.process(second)).thenReturn(KakaoUnlinkProcessingResult.SUCCEEDED);

        worker.runBatch();

        verify(taskProcessor).process(second);
        assertThat(output)
                .contains("failureType=java.lang.IllegalStateException")
                .contains("java.lang.IllegalStateException: unexpected-worker-failure")
                .doesNotContain(first.claimToken());
    }

    private KakaoUnlinkClaim claim(Long taskId) {
        return new KakaoUnlinkClaim(taskId, taskId, taskId, 1L, "opaque-token-" + taskId, 0);
    }
}
