package com.gather.gather.domain.auth.kakao.worker;

import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus;
import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControl;
import com.gather.gather.domain.auth.repository.KakaoUnlinkTaskRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkWorkerControlRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoUnlinkClaimService {

    private final KakaoUnlinkWorkerControlRepository controlRepository;
    private final KakaoUnlinkTaskRepository taskRepository;
    private final KakaoUnlinkWorkerProperties properties;
    private final KakaoUnlinkClaimTokenGenerator tokenGenerator;

    @Transactional
    public List<KakaoUnlinkClaim> claimBatch() {
        /*
         * Global lock order: WorkerControl -> SocialAccount -> KakaoUnlinkTask -> User.
         * Claim only needs WorkerControl -> KakaoUnlinkTask and must preserve that subsequence.
         */
        KakaoUnlinkWorkerControl control =
                controlRepository
                        .findSingletonForUpdate()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Kakao unlink worker control이 없습니다."));
        if (!control.isActive()) {
            return List.of();
        }

        LocalDateTime databaseNow = taskRepository.currentUtcDateTime();
        LocalDateTime leaseExpiresAt = databaseNow.plus(properties.leaseDuration());
        List<KakaoUnlinkTask> selected =
                new ArrayList<>(
                        taskRepository.findDuePendingForUpdate(
                                databaseNow, properties.batchSize()));
        int remaining = properties.batchSize() - selected.size();
        if (remaining > 0) {
            selected.addAll(taskRepository.findExpiredProcessingForUpdate(databaseNow, remaining));
        }

        List<KakaoUnlinkClaim> claims = new ArrayList<>(selected.size());
        for (KakaoUnlinkTask task : selected) {
            String token = tokenGenerator.generate();
            if (task.getStatus() == KakaoUnlinkTaskStatus.PENDING) {
                task.claim(token, properties.workerIdentifier(), databaseNow, leaseExpiresAt);
            } else {
                task.reclaim(token, properties.workerIdentifier(), databaseNow, leaseExpiresAt);
            }
            claims.add(toClaim(task, token));
        }
        return List.copyOf(claims);
    }

    private KakaoUnlinkClaim toClaim(KakaoUnlinkTask task, String claimToken) {
        return new KakaoUnlinkClaim(
                task.getId(),
                task.getSocialAccount().getId(),
                task.getSocialAccount().getUser().getId(),
                task.getGeneration(),
                claimToken,
                task.getRetryCycle());
    }
}
