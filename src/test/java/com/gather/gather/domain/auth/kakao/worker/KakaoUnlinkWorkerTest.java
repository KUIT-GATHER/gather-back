package com.gather.gather.domain.auth.kakao.worker;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminApiClient;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkDisposition;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoUnlinkWorkerTest {

    @Mock private KakaoUnlinkClaimService claimService;
    @Mock private KakaoUnlinkTransactionService transactionService;
    @Mock private KakaoAdminApiClient adminApiClient;
    @Mock private KakaoUnlinkResultService resultService;

    private KakaoUnlinkWorker worker;

    @BeforeEach
    void setUp() {
        worker =
                new KakaoUnlinkWorker(
                        claimService, transactionService, adminApiClient, resultService);
    }

    @Test
    void alreadyUnlinked_finalizesLocallyWithoutReservationOrHttp() {
        KakaoUnlinkClaim claim = claim(1L);
        when(claimService.claimBatch()).thenReturn(List.of(claim));
        when(transactionService.preflight(claim))
                .thenReturn(KakaoUnlinkPreflightOutcome.LOCAL_FINALIZE);

        worker.runBatch();

        verify(resultService).finalizeLocally(claim);
        verify(transactionService, never()).reserveAttempt(claim);
        verify(adminApiClient, never()).unlink(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void configurationFailure_stopsBeforeRemainingClaimedTask() {
        KakaoUnlinkClaim first = claim(1L);
        KakaoUnlinkClaim second = claim(2L);
        KakaoUnlinkAttempt attempt = new KakaoUnlinkAttempt(first, 123L, 1);
        KakaoAdminUnlinkResult configurationFailure =
                new KakaoAdminUnlinkResult(
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION, 401, -401, null);
        when(claimService.claimBatch()).thenReturn(List.of(first, second));
        when(transactionService.preflight(first)).thenReturn(KakaoUnlinkPreflightOutcome.RESERVE);
        when(transactionService.reserveAttempt(first))
                .thenReturn(KakaoUnlinkReservation.reserved(attempt));
        when(adminApiClient.unlink(123L)).thenReturn(configurationFailure);
        when(resultService.applyConfigurationFailure(attempt, configurationFailure))
                .thenReturn(KakaoUnlinkBatchAction.STOP_BATCH);

        worker.runBatch();

        verify(transactionService, never()).preflight(second);
        verify(transactionService, never()).reserveAttempt(second);
    }

    private KakaoUnlinkClaim claim(Long taskId) {
        return new KakaoUnlinkClaim(taskId, taskId, taskId, 1L, "opaque-token", 0, 0);
    }
}
