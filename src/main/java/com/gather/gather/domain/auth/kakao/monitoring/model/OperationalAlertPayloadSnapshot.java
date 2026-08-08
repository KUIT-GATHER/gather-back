package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertEventType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertSeverity;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import java.time.Instant;

public record OperationalAlertPayloadSnapshot(
        int schemaVersion,
        String fingerprint,
        KakaoUnlinkAlertType alertType,
        int occurrenceNo,
        KakaoUnlinkAlertSeverity severity,
        KakaoUnlinkAlertEventType eventType,
        int eventSequence,
        KakaoUnlinkAlertChannel channel,
        Instant generatedAt,
        KakaoUnlinkIncidentSafeDetails details) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public OperationalAlertPayloadSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 운영 알림 payload schema version입니다.");
        }
        new KakaoUnlinkIncidentFingerprint(fingerprint);
        if (alertType == null
                || severity == null
                || eventType == null
                || channel == null
                || generatedAt == null
                || details == null
                || occurrenceNo < 1) {
            throw new IllegalArgumentException("운영 알림 payload 필수 값이 누락되었습니다.");
        }
        eventType.validateSequence(eventSequence);
        if (!details.supports(alertType)) {
            throw new IllegalArgumentException("alert type과 safe details 종류가 일치하지 않습니다.");
        }
        if ((alertType == KakaoUnlinkAlertType.SYNTHETIC_TEST)
                != (eventType == KakaoUnlinkAlertEventType.TEST)) {
            throw new IllegalArgumentException("synthetic incident에는 TEST delivery만 허용됩니다.");
        }
    }
}
