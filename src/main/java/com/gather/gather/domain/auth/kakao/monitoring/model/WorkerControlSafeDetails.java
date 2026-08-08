package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskErrorType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControlStatus;
import java.time.Instant;
import java.util.Set;

public record WorkerControlSafeDetails(
        KakaoUnlinkWorkerControlStatus status,
        KakaoUnlinkTaskErrorType blockedReason,
        Instant lastPollStartedAt,
        Instant lastPollCompletedAt,
        Instant lastPollFailedAt,
        KakaoUnlinkOperationalFailureType lastPollFailureType,
        Integer lastHttpStatus,
        Integer lastKakaoCode)
        implements KakaoUnlinkIncidentSafeDetails {

    private static final Set<KakaoUnlinkAlertType> SUPPORTED_TYPES =
            Set.of(
                    KakaoUnlinkAlertType.WORKER_CONFIGURATION_BLOCKED,
                    KakaoUnlinkAlertType.WORKER_HEARTBEAT_MISSED);

    public WorkerControlSafeDetails {
        if (status == null) {
            throw new IllegalArgumentException("worker control 상태는 필수입니다.");
        }
    }

    @Override
    public boolean supports(KakaoUnlinkAlertType alertType) {
        return SUPPORTED_TYPES.contains(alertType);
    }
}
