package com.gather.gather.domain.auth.kakao.monitoring.model;

public record KakaoUnlinkIncidentResolution(KakaoUnlinkMonitorLease lease, long incidentId) {
    public KakaoUnlinkIncidentResolution {
        if (lease == null || incidentId <= 0) {
            throw new IllegalArgumentException("incident resolution 값이 올바르지 않습니다.");
        }
    }
}
