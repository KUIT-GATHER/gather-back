package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertSeverity;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import java.time.LocalDateTime;
import java.util.Set;

public record KakaoUnlinkIncidentObservation(
        KakaoUnlinkMonitorLease lease,
        KakaoUnlinkIncidentFingerprint fingerprint,
        KakaoUnlinkAlertType alertType,
        KakaoUnlinkAlertSeverity severity,
        KakaoUnlinkIncidentSafeDetails safeDetails,
        LocalDateTime nextDiscordReminderAt,
        LocalDateTime nextEmailReminderAt,
        Set<KakaoUnlinkAlertChannel> initialChannels,
        Set<KakaoUnlinkAlertChannel> escalationChannels) {

    public KakaoUnlinkIncidentObservation {
        if (lease == null
                || fingerprint == null
                || alertType == null
                || severity == null
                || safeDetails == null) {
            throw new IllegalArgumentException("incident observation 필수 값이 누락되었습니다.");
        }
        if (alertType == KakaoUnlinkAlertType.SYNTHETIC_TEST || !safeDetails.supports(alertType)) {
            throw new IllegalArgumentException("operational observation의 alert type이 올바르지 않습니다.");
        }
        if (fingerprint.alertType() != alertType) {
            throw new IllegalArgumentException("fingerprint와 alert type이 일치하지 않습니다.");
        }
        initialChannels = Set.copyOf(initialChannels == null ? Set.of() : initialChannels);
        escalationChannels = Set.copyOf(escalationChannels == null ? Set.of() : escalationChannels);
    }
}
