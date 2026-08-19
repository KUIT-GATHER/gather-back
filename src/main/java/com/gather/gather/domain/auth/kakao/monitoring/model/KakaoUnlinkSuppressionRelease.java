package com.gather.gather.domain.auth.kakao.monitoring.model;

import java.time.LocalDateTime;

public record KakaoUnlinkSuppressionRelease(
        KakaoUnlinkMonitorLease lease, long incidentId, LocalDateTime eligibleAt) {
    public KakaoUnlinkSuppressionRelease {
        if (lease == null || incidentId <= 0) {
            throw new IllegalArgumentException("suppression release 값이 올바르지 않습니다.");
        }
    }
}
