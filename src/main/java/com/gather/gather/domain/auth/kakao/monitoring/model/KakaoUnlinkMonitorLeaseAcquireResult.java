package com.gather.gather.domain.auth.kakao.monitoring.model;

public record KakaoUnlinkMonitorLeaseAcquireResult(Outcome outcome, KakaoUnlinkMonitorLease lease) {

    public enum Outcome {
        ACQUIRED,
        BUSY
    }

    public KakaoUnlinkMonitorLeaseAcquireResult {
        if (outcome == null || (outcome == Outcome.ACQUIRED) != (lease != null)) {
            throw new IllegalArgumentException("monitor lease 획득 결과가 올바르지 않습니다.");
        }
    }

    public static KakaoUnlinkMonitorLeaseAcquireResult acquired(KakaoUnlinkMonitorLease lease) {
        return new KakaoUnlinkMonitorLeaseAcquireResult(Outcome.ACQUIRED, lease);
    }

    public static KakaoUnlinkMonitorLeaseAcquireResult busy() {
        return new KakaoUnlinkMonitorLeaseAcquireResult(Outcome.BUSY, null);
    }
}
