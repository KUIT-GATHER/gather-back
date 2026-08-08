package com.gather.gather.domain.auth.kakao.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertEventType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertSeverity;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskErrorType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus;
import com.gather.gather.domain.auth.kakao.monitoring.model.DeadTaskSafeDetails;
import com.gather.gather.domain.auth.kakao.monitoring.model.DeadTaskSample;
import com.gather.gather.domain.auth.kakao.monitoring.model.DeadTaskSummarySafeDetails;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentFingerprint;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSafeDetails;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkMonitorLease;
import com.gather.gather.domain.auth.kakao.monitoring.model.OperationalAlertPayloadSnapshot;
import com.gather.gather.domain.auth.kakao.monitoring.model.SyntheticTestSafeDetails;
import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkMonitoringJsonCodec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class KakaoUnlinkMonitoringModelTest {

    @Test
    void severityOrdering_neverTreatsLowerSeverityAsHigher() {
        assertThat(KakaoUnlinkAlertSeverity.CRITICAL.isHigherThan(KakaoUnlinkAlertSeverity.WARNING))
                .isTrue();
        assertThat(KakaoUnlinkAlertSeverity.WARNING.isHigherThan(KakaoUnlinkAlertSeverity.INFO))
                .isTrue();
        assertThat(KakaoUnlinkAlertSeverity.INFO.isHigherThan(KakaoUnlinkAlertSeverity.CRITICAL))
                .isFalse();
        assertThat(KakaoUnlinkAlertSeverity.WARNING.isHigherThan(KakaoUnlinkAlertSeverity.WARNING))
                .isFalse();
    }

    @Test
    void eventSequence_enforcesFixedInitialAndRecoveredSequence() {
        KakaoUnlinkAlertEventType.INITIAL.validateSequence(1);
        KakaoUnlinkAlertEventType.RECOVERED.validateSequence(1);
        KakaoUnlinkAlertEventType.REMINDER.validateSequence(7);
        KakaoUnlinkAlertEventType.ESCALATED.validateSequence(2);
        KakaoUnlinkAlertEventType.TEST.validateSequence(3);

        assertThatThrownBy(() -> KakaoUnlinkAlertEventType.INITIAL.validateSequence(2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KakaoUnlinkAlertEventType.RECOVERED.validateSequence(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void safeDetails_rejectsUnsafeRangesAndOversizedSamples() {
        assertThatThrownBy(
                        () ->
                                new DeadTaskSafeDetails(
                                        0,
                                        0,
                                        0,
                                        KakaoUnlinkTaskStatus.DEAD,
                                        KakaoUnlinkTaskErrorType.REQUEST,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);

        DeadTaskSample sample =
                new DeadTaskSample(
                        1,
                        0,
                        1,
                        KakaoUnlinkTaskStatus.DEAD,
                        KakaoUnlinkTaskErrorType.REQUEST,
                        400,
                        -1);
        assertThatThrownBy(
                        () ->
                                new DeadTaskSummarySafeDetails(
                                        6, List.of(sample, sample, sample, sample, sample, sample)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payloadSnapshot_rejectsAlertDetailsMismatchAndSyntheticNonTestEvent() {
        DeadTaskSafeDetails deadDetails = deadDetails();
        assertThatThrownBy(
                        () ->
                                snapshot(
                                        KakaoUnlinkIncidentFingerprint.SYNTHETIC_TEST_VALUE,
                                        KakaoUnlinkAlertType.SYNTHETIC_TEST,
                                        KakaoUnlinkAlertEventType.INITIAL,
                                        1,
                                        new SyntheticTestSafeDetails()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                snapshot(
                                        "KAKAO_UNLINK:DEAD_TASK:1:0",
                                        KakaoUnlinkAlertType.BACKLOG_ACCUMULATION,
                                        KakaoUnlinkAlertEventType.INITIAL,
                                        1,
                                        deadDetails))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void monitorLease_toStringRedactsToken() {
        LocalDateTime acquiredAt = LocalDateTime.of(2026, 8, 8, 0, 0);
        KakaoUnlinkMonitorLease lease =
                new KakaoUnlinkMonitorLease(
                        1,
                        "monitor-1",
                        "secret-lease-token",
                        acquiredAt,
                        acquiredAt.plusMinutes(3));

        assertThat(lease.toString()).contains("<redacted>").doesNotContain("secret-lease-token");
    }

    @Test
    void fingerprint_rejectsNonAllowlistedCharacters() {
        assertThatThrownBy(
                        () -> new KakaoUnlinkIncidentFingerprint("KAKAO_UNLINK:user@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typedJsonContainsStableDiscriminatorAndRoundTrips() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        KakaoUnlinkMonitoringJsonCodec codec = new KakaoUnlinkMonitoringJsonCodec(objectMapper);
        DeadTaskSafeDetails details = deadDetails();
        OperationalAlertPayloadSnapshot payload =
                snapshot(
                        "KAKAO_UNLINK:DEAD_TASK:1:0",
                        KakaoUnlinkAlertType.DEAD_TASK,
                        KakaoUnlinkAlertEventType.INITIAL,
                        1,
                        details);

        String detailsJson = codec.write(details);
        String payloadJson = codec.write(payload);

        assertThat(detailsJson).contains("\"kind\":\"DEAD_TASK\"");
        assertThat(payloadJson).contains("\"kind\":\"DEAD_TASK\"");
        assertThat(objectMapper.readValue(detailsJson, KakaoUnlinkIncidentSafeDetails.class))
                .isEqualTo(details);
        assertThat(objectMapper.readValue(payloadJson, OperationalAlertPayloadSnapshot.class))
                .isEqualTo(payload);
    }

    private DeadTaskSafeDetails deadDetails() {
        return new DeadTaskSafeDetails(
                1, 0, 1, KakaoUnlinkTaskStatus.DEAD, KakaoUnlinkTaskErrorType.REQUEST, 400, -1);
    }

    private OperationalAlertPayloadSnapshot snapshot(
            String fingerprint,
            KakaoUnlinkAlertType alertType,
            KakaoUnlinkAlertEventType eventType,
            int eventSequence,
            com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSafeDetails
                    details) {
        return new OperationalAlertPayloadSnapshot(
                OperationalAlertPayloadSnapshot.CURRENT_SCHEMA_VERSION,
                fingerprint,
                alertType,
                1,
                KakaoUnlinkAlertSeverity.WARNING,
                eventType,
                eventSequence,
                KakaoUnlinkAlertChannel.DISCORD,
                Instant.parse("2026-08-08T00:00:00Z"),
                details);
    }
}
