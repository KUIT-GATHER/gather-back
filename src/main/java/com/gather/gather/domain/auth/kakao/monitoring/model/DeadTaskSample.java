package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskErrorType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus;

public record DeadTaskSample(
        long taskId,
        int retryCycle,
        int attemptCount,
        KakaoUnlinkTaskStatus taskStatus,
        KakaoUnlinkTaskErrorType lastErrorType,
        Integer lastHttpStatus,
        Integer lastKakaoCode) {

    public DeadTaskSample {
        if (taskId <= 0 || retryCycle < 0 || attemptCount < 0 || taskStatus == null) {
            throw new IllegalArgumentException("DEAD task 표본 값이 올바르지 않습니다.");
        }
    }
}
