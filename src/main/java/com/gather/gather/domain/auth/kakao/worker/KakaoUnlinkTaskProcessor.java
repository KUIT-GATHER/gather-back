package com.gather.gather.domain.auth.kakao.worker;

import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminApiClient;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkDisposition;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kakao.admin.enabled", havingValue = "true")
public class KakaoUnlinkTaskProcessor {

    private final KakaoUnlinkTransactionService transactionService;
    private final KakaoAdminApiClient adminApiClient;
    private final KakaoUnlinkResultService resultService;

    public KakaoUnlinkProcessingResult process(KakaoUnlinkClaim claim) {
        KakaoUnlinkPreflightOutcome preflight = transactionService.preflight(claim);
        switch (preflight) {
            case CLAIM_LOST -> {
                return KakaoUnlinkProcessingResult.CLAIM_LOST;
            }
            case STALE -> {
                return transactionService.markStale(claim);
            }
            case LOCAL_FINALIZE -> {
                return resultService.finalizeLocally(claim);
            }
            case RESERVE -> {
                // reservation transaction이 끝난 뒤에만 외부 HTTP를 호출한다.
            }
        }

        KakaoUnlinkReservation reservation = transactionService.reserveAttempt(claim);
        switch (reservation.outcome()) {
            case BLOCKED -> {
                return KakaoUnlinkProcessingResult.CONFIGURATION_BLOCKED;
            }
            case CLAIM_LOST -> {
                return KakaoUnlinkProcessingResult.CLAIM_LOST;
            }
            case STALE -> {
                return KakaoUnlinkProcessingResult.STALE;
            }
            case DEAD -> {
                return KakaoUnlinkProcessingResult.DEAD;
            }
            case RESERVED -> {
                // 외부 호출은 아래의 transaction guard를 통과한 뒤 실행한다.
            }
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Kakao Admin API는 DB transaction 밖에서 호출해야 합니다.");
        }
        KakaoUnlinkAttempt attempt = reservation.attempt();
        KakaoAdminUnlinkResult result = adminApiClient.unlink(attempt.kakaoUserId());
        if (result.disposition() == KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION) {
            return resultService.applyConfigurationFailure(attempt, result);
        }
        return resultService.apply(attempt, result);
    }
}
