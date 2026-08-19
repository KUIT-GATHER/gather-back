package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskErrorType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus;

public record DeadTaskSafeDetails(
        long taskId,
        int retryCycle,
        int attemptCount,
        KakaoUnlinkTaskStatus taskStatus,
        KakaoUnlinkTaskErrorType lastErrorType,
        Integer lastHttpStatus,
        Integer lastKakaoCode)
        implements KakaoUnlinkIncidentSafeDetails {

    public DeadTaskSafeDetails {
        new DeadTaskSample(
                taskId,
                retryCycle,
                attemptCount,
                taskStatus,
                lastErrorType,
                lastHttpStatus,
                lastKakaoCode);
    }

    @Override
    public boolean supports(KakaoUnlinkAlertType alertType) {
        return alertType == KakaoUnlinkAlertType.DEAD_TASK;
    }
}
