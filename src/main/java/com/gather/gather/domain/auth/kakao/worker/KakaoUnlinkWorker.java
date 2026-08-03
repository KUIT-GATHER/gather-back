package com.gather.gather.domain.auth.kakao.worker;

import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminApiClient;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkDisposition;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "kakao.admin",
        name = {"enabled", "unlink-worker.enabled"},
        havingValue = "true")
public class KakaoUnlinkWorker {

    private final KakaoUnlinkClaimService claimService;
    private final KakaoUnlinkTransactionService transactionService;
    private final KakaoAdminApiClient adminApiClient;
    private final KakaoUnlinkResultService resultService;

    public void runBatch() {
        List<KakaoUnlinkClaim> claims = claimService.claimBatch();
        if (claims.isEmpty()) {
            return;
        }
        int processedCount = 0;
        for (KakaoUnlinkClaim claim : claims) {
            try {
                if (process(claim) == KakaoUnlinkBatchAction.STOP_BATCH) {
                    log.warn(
                            "Kakao unlink batch stopped: claimedCount={}, processedCount={}, taskId={}",
                            claims.size(),
                            processedCount + 1,
                            claim.taskId());
                    return;
                }
                processedCount++;
            } catch (RuntimeException exception) {
                log.error(
                        "Kakao unlink task processing failed unexpectedly: taskId={}, failureType={}",
                        claim.taskId(),
                        exception.getClass().getName(),
                        exception);
            }
        }
        log.info(
                "Kakao unlink batch completed: claimedCount={}, processedCount={}",
                claims.size(),
                processedCount);
    }

    private KakaoUnlinkBatchAction process(KakaoUnlinkClaim claim) {
        KakaoUnlinkPreflightOutcome preflight = transactionService.preflight(claim);
        switch (preflight) {
            case CLAIM_LOST -> {
                return KakaoUnlinkBatchAction.CONTINUE;
            }
            case STALE -> {
                transactionService.markStale(claim);
                return KakaoUnlinkBatchAction.CONTINUE;
            }
            case LOCAL_FINALIZE -> {
                resultService.finalizeLocally(claim);
                return KakaoUnlinkBatchAction.CONTINUE;
            }
            case RESERVE -> {
                // reservation transaction이 끝난 뒤에만 외부 HTTP를 호출한다.
            }
        }

        KakaoUnlinkReservation reservation = transactionService.reserveAttempt(claim);
        if (reservation.outcome() == KakaoUnlinkReservation.Outcome.BLOCKED) {
            return KakaoUnlinkBatchAction.STOP_BATCH;
        }
        if (reservation.outcome() != KakaoUnlinkReservation.Outcome.RESERVED) {
            return KakaoUnlinkBatchAction.CONTINUE;
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
