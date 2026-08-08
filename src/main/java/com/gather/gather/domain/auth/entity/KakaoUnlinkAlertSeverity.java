package com.gather.gather.domain.auth.entity;

public enum KakaoUnlinkAlertSeverity {
    INFO,
    WARNING,
    CRITICAL;

    public boolean isHigherThan(KakaoUnlinkAlertSeverity other) {
        if (other == null) {
            throw new IllegalArgumentException("비교할 기존 알림 심각도는 필수입니다.");
        }
        return ordinal() > other.ordinal();
    }
}
