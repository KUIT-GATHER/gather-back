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
        if (taskId <= 0
                || retryCycle < 0
                || attemptCount < 0
                || taskStatus == null
                || !isValidHttpStatus(lastHttpStatus)
                || !isValidKakaoCode(lastKakaoCode)) {
            throw new IllegalArgumentException("DEAD task 표본 값이 올바르지 않습니다.");
        }
    }

    private static boolean isValidHttpStatus(Integer status) {
        return status == null || (status >= 100 && status <= 599);
    }

    private static boolean isValidKakaoCode(Integer code) {
        return code == null || code <= 0;
    }
}
