package com.gather.gather.domain.auth.entity;

public enum KakaoUnlinkAlertEventType {
    INITIAL,
    REMINDER,
    ESCALATED,
    RECOVERED,
    TEST;

    public void validateSequence(int eventSequence) {
        if (eventSequence < 1) {
            throw new IllegalArgumentException("알림 event sequence는 1 이상이어야 합니다.");
        }
        if ((this == INITIAL || this == RECOVERED) && eventSequence != 1) {
            throw new IllegalArgumentException(name() + " event sequence는 1이어야 합니다.");
        }
    }
}
