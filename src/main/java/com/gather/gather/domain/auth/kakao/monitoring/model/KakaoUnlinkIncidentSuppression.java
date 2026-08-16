package com.gather.gather.domain.auth.kakao.monitoring.model;

public record KakaoUnlinkIncidentSuppression(
        KakaoUnlinkMonitorLease lease,
        long incidentId,
        long suppressingIncidentId,
        int suppressingOccurrenceNo) {
    public KakaoUnlinkIncidentSuppression {
        if (lease == null
                || incidentId <= 0
                || suppressingIncidentId <= 0
                || incidentId == suppressingIncidentId
                || suppressingOccurrenceNo < 1) {
            throw new IllegalArgumentException("incident suppression 값이 올바르지 않습니다.");
        }
    }
}
