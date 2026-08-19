package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertSeverity;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncident;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncidentStatus;
import com.gather.gather.domain.auth.entity.KakaoUnlinkNotificationState;
import java.time.LocalDateTime;

public record KakaoUnlinkIncidentSnapshot(
        long id,
        String fingerprint,
        KakaoUnlinkAlertType alertType,
        KakaoUnlinkAlertSeverity severity,
        KakaoUnlinkIncidentStatus status,
        int occurrenceNo,
        int severityEscalationNo,
        KakaoUnlinkNotificationState notificationState,
        LocalDateTime openedAt,
        LocalDateTime lastObservedAt,
        LocalDateTime resolvedAt) {

    public static KakaoUnlinkIncidentSnapshot from(KakaoUnlinkIncident incident) {
        return new KakaoUnlinkIncidentSnapshot(
                incident.getId(),
                incident.getFingerprint(),
                incident.getAlertType(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getOccurrenceNo(),
                incident.getSeverityEscalationNo(),
                incident.getNotificationState(),
                incident.getOpenedAt(),
                incident.getLastObservedAt(),
                incident.getResolvedAt());
    }
}
