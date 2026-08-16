package com.gather.gather.domain.auth.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KakaoUnlinkAlertSeverity {
    INFO(10),
    WARNING(20),
    CRITICAL(30);

    private final int level;

    public boolean isHigherThan(KakaoUnlinkAlertSeverity other) {
        if (other == null) {
            throw new IllegalArgumentException("비교할 기존 알림 심각도는 필수입니다.");
        }
        return level > other.level;
    }
}
