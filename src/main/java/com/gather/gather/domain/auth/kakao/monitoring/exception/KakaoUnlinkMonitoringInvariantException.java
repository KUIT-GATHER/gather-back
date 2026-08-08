package com.gather.gather.domain.auth.kakao.monitoring.exception;

public class KakaoUnlinkMonitoringInvariantException extends RuntimeException {

    public KakaoUnlinkMonitoringInvariantException(String message) {
        super(message);
    }

    public KakaoUnlinkMonitoringInvariantException(String message, Throwable cause) {
        super(message, cause);
    }
}
