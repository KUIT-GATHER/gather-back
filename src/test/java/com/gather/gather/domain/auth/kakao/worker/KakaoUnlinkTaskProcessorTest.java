package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminApiClient;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkDisposition;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoUnlinkTaskProcessorTest {

    @Mock private KakaoUnlinkTransactionService transactionService;
    @Mock private KakaoAdminApiClient adminApiClient;
    @Mock private KakaoUnlinkResultService resultService;

    private KakaoUnlinkTaskProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new KakaoUnlinkTaskProcessor(transactionService, adminApiClient, resultService);
    }

    @Test
    void preflightClaimLost_returnsClaimLostWithoutReservationOrHttp() {
        KakaoUnlinkClaim claim = claim();
        when(transactionService.preflight(claim))
                .thenReturn(KakaoUnlinkPreflightOutcome.CLAIM_LOST);

        assertThat(processor.process(claim)).isEqualTo(KakaoUnlinkProcessingResult.CLAIM_LOST);
        verify(transactionService, never()).reserveAttempt(claim);
        verify(adminApiClient, never()).unlink(anyLong());
    }

    @Test
    void preflightStale_returnsActualStaleApplicationResult() {
        KakaoUnlinkClaim claim = claim();
        when(transactionService.preflight(claim)).thenReturn(KakaoUnlinkPreflightOutcome.STALE);
        when(transactionService.markStale(claim)).thenReturn(KakaoUnlinkProcessingResult.STALE);

        assertThat(processor.process(claim)).isEqualTo(KakaoUnlinkProcessingResult.STALE);
    }

    @Test
    void preflightStale_whenClaimIsLost_returnsClaimLost() {
        KakaoUnlinkClaim claim = claim();
        when(transactionService.preflight(claim)).thenReturn(KakaoUnlinkPreflightOutcome.STALE);
        when(transactionService.markStale(claim))
                .thenReturn(KakaoUnlinkProcessingResult.CLAIM_LOST);

        assertThat(processor.process(claim)).isEqualTo(KakaoUnlinkProcessingResult.CLAIM_LOST);
    }

    @Test
    void localFinalize_returnsActualApplicationResultWithoutHttp() {
        KakaoUnlinkClaim claim = claim();
        when(transactionService.preflight(claim))
                .thenReturn(KakaoUnlinkPreflightOutcome.LOCAL_FINALIZE);
        when(resultService.finalizeLocally(claim))
                .thenReturn(KakaoUnlinkProcessingResult.SUCCEEDED);

        assertThat(processor.process(claim)).isEqualTo(KakaoUnlinkProcessingResult.SUCCEEDED);
        verify(adminApiClient, never()).unlink(anyLong());
    }

    @Test
    void nonReservedOutcomes_areMappedWithoutHttp() {
        assertReservationOutcome(
                KakaoUnlinkReservation.Outcome.BLOCKED,
                KakaoUnlinkProcessingResult.CONFIGURATION_BLOCKED);
        assertReservationOutcome(
                KakaoUnlinkReservation.Outcome.CLAIM_LOST, KakaoUnlinkProcessingResult.CLAIM_LOST);
        assertReservationOutcome(
                KakaoUnlinkReservation.Outcome.STALE, KakaoUnlinkProcessingResult.STALE);
        assertReservationOutcome(
                KakaoUnlinkReservation.Outcome.DEAD, KakaoUnlinkProcessingResult.DEAD);
    }

    @Test
    void reservedSuccess_returnsActualResultServiceOutcome() {
        KakaoUnlinkClaim claim = claim();
        KakaoUnlinkAttempt attempt = new KakaoUnlinkAttempt(claim, 123L, 1);
        KakaoAdminUnlinkResult result =
                new KakaoAdminUnlinkResult(KakaoAdminUnlinkDisposition.SUCCESS, 200, null, null);
        when(transactionService.preflight(claim)).thenReturn(KakaoUnlinkPreflightOutcome.RESERVE);
        when(transactionService.reserveAttempt(claim))
                .thenReturn(KakaoUnlinkReservation.reserved(attempt));
        when(adminApiClient.unlink(123L)).thenReturn(result);
        when(resultService.apply(attempt, result))
                .thenReturn(KakaoUnlinkProcessingResult.SUCCEEDED);

        assertThat(processor.process(claim)).isEqualTo(KakaoUnlinkProcessingResult.SUCCEEDED);
    }

    @Test
    void configurationFailure_prioritizesControlBlockedResult() {
        KakaoUnlinkClaim claim = claim();
        KakaoUnlinkAttempt attempt = new KakaoUnlinkAttempt(claim, 123L, 1);
        KakaoAdminUnlinkResult result =
                new KakaoAdminUnlinkResult(
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION, 401, -401, null);
        when(transactionService.preflight(claim)).thenReturn(KakaoUnlinkPreflightOutcome.RESERVE);
        when(transactionService.reserveAttempt(claim))
                .thenReturn(KakaoUnlinkReservation.reserved(attempt));
        when(adminApiClient.unlink(123L)).thenReturn(result);
        when(resultService.applyConfigurationFailure(attempt, result))
                .thenReturn(KakaoUnlinkProcessingResult.CONFIGURATION_BLOCKED);

        assertThat(processor.process(claim))
                .isEqualTo(KakaoUnlinkProcessingResult.CONFIGURATION_BLOCKED);
        verify(resultService, never()).apply(attempt, result);
    }

    @Test
    void unexpectedException_isPropagated() {
        KakaoUnlinkClaim claim = claim();
        when(transactionService.preflight(claim))
                .thenThrow(new IllegalStateException("unexpected"));

        assertThatThrownBy(() -> processor.process(claim))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("unexpected");
    }

    private void assertReservationOutcome(
            KakaoUnlinkReservation.Outcome reservationOutcome,
            KakaoUnlinkProcessingResult expected) {
        KakaoUnlinkClaim claim = claim();
        when(transactionService.preflight(claim)).thenReturn(KakaoUnlinkPreflightOutcome.RESERVE);
        when(transactionService.reserveAttempt(claim))
                .thenReturn(KakaoUnlinkReservation.of(reservationOutcome));

        assertThat(processor.process(claim)).isEqualTo(expected);
        verify(adminApiClient, never()).unlink(anyLong());
    }

    private KakaoUnlinkClaim claim() {
        return new KakaoUnlinkClaim(1L, 2L, 3L, 1L, "opaque-token", 0);
    }
}
