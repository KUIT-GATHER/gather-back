package com.gather.gather.domain.auth.kakao.worker;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "kakao.admin",
        name = {"enabled", "unlink-worker.enabled"},
        havingValue = "true")
public class KakaoUnlinkWorker {

    private final KakaoUnlinkClaimService claimService;
    private final KakaoUnlinkTaskProcessor taskProcessor;

    public void runBatch() {
        List<KakaoUnlinkClaim> claims = claimService.claimBatch();
        if (claims.isEmpty()) {
            return;
        }
        int processedCount = 0;
        for (KakaoUnlinkClaim claim : claims) {
            try {
                KakaoUnlinkProcessingResult result = taskProcessor.process(claim);
                if (result == KakaoUnlinkProcessingResult.CONFIGURATION_BLOCKED) {
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
}
