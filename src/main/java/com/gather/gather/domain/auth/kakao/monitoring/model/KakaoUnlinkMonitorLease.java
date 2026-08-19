package com.gather.gather.domain.auth.kakao.monitoring.model;

import java.time.LocalDateTime;

public record KakaoUnlinkMonitorLease(
        long scanSequence,
        String owner,
        String token,
        LocalDateTime acquiredAt,
        LocalDateTime expiresAt) {

    public KakaoUnlinkMonitorLease {
        if (scanSequence < 1
                || owner == null
                || owner.isBlank()
                || owner.length() > 128
                || token == null
                || token.isBlank()
                || token.length() > 64
                || acquiredAt == null
                || expiresAt == null
                || !expiresAt.isAfter(acquiredAt)) {
            throw new IllegalArgumentException("monitor lease 값이 올바르지 않습니다.");
        }
    }

    @Override
    public String toString() {
        return "KakaoUnlinkMonitorLease[scanSequence="
                + scanSequence
                + ", owner="
                + owner
                + ", token=<redacted>, acquiredAt="
                + acquiredAt
                + ", expiresAt="
                + expiresAt
                + "]";
    }
}
