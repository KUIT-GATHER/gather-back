package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus;
import java.util.Set;

public record TaskPopulationSafeDetails(
        KakaoUnlinkTaskStatus taskStatus,
        int affectedCount,
        long oldestAgeSeconds,
        long thresholdSeconds)
        implements KakaoUnlinkIncidentSafeDetails {

    private static final Set<KakaoUnlinkAlertType> SUPPORTED_TYPES =
            Set.of(
                    KakaoUnlinkAlertType.OVERDUE_PENDING,
                    KakaoUnlinkAlertType.EXPIRED_PROCESSING,
                    KakaoUnlinkAlertType.BACKLOG_ACCUMULATION);

    public TaskPopulationSafeDetails {
        if (taskStatus == null
                || affectedCount < 1
                || oldestAgeSeconds < 0
                || thresholdSeconds < 0) {
            throw new IllegalArgumentException("task population 관측 값이 올바르지 않습니다.");
        }
    }

    @Override
    public boolean supports(KakaoUnlinkAlertType alertType) {
        return SUPPORTED_TYPES.contains(alertType);
    }
}
