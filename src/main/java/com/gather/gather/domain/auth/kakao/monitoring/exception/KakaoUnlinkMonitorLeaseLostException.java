package com.gather.gather.domain.auth.kakao.monitoring.exception;

public class KakaoUnlinkMonitorLeaseLostException extends RuntimeException {

    public KakaoUnlinkMonitorLeaseLostException() {
        super("유효한 Kakao unlink monitor scan lease가 없습니다.");
    }
}
