package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.kakao.monitoring.model.DeadTaskSafeDetails;
import com.gather.gather.domain.auth.kakao.monitoring.model.SyntheticTestSafeDetails;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class KakaoUnlinkIncidentTest {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 8, 8, 0, 0);

    @Test
    void observeEscalatesWithoutDowngradeAndReopenStartsNewOccurrence() {
        KakaoUnlinkIncident incident = operationalIncident(1L, "KAKAO_UNLINK:DEAD_TASK:1:0");

        KakaoUnlinkIncidentTransition escalated =
                incident.observe(
                        KakaoUnlinkAlertType.DEAD_TASK,
                        KakaoUnlinkAlertSeverity.CRITICAL,
                        2,
                        OPENED_AT.plusMinutes(1),
                        deadDetails(),
                        null,
                        null);
        KakaoUnlinkIncidentTransition lower =
                incident.observe(
                        KakaoUnlinkAlertType.DEAD_TASK,
                        KakaoUnlinkAlertSeverity.INFO,
                        3,
                        OPENED_AT.plusMinutes(2),
                        deadDetails(),
                        null,
                        null);

        assertThat(escalated.severityEscalated()).isTrue();
        assertThat(lower.severityEscalated()).isFalse();
        assertThat(incident.getSeverity()).isEqualTo(KakaoUnlinkAlertSeverity.CRITICAL);
        assertThat(incident.getSeverityEscalationNo()).isEqualTo(1);

        incident.resolve(OPENED_AT.plusMinutes(3));
        KakaoUnlinkIncidentTransition reopened =
                incident.observe(
                        KakaoUnlinkAlertType.DEAD_TASK,
                        KakaoUnlinkAlertSeverity.WARNING,
                        4,
                        OPENED_AT.plusMinutes(4),
                        deadDetails(),
                        null,
                        null);

        assertThat(reopened.reopened()).isTrue();
        assertThat(incident.getOccurrenceNo()).isEqualTo(2);
        assertThat(incident.getSeverityEscalationNo()).isZero();
        assertThat(incident.getStatus()).isEqualTo(KakaoUnlinkIncidentStatus.OPEN);
    }

    @Test
    void suppressionRequiresDifferentOpenOperationalIncidentAndKeepsObservationState() {
        KakaoUnlinkIncident child = operationalIncident(1L, "KAKAO_UNLINK:DEAD_TASK:1:0");
        KakaoUnlinkIncident cause = operationalIncident(2L, "KAKAO_UNLINK:DEAD_TASK:2:0");

        child.suppressBy(cause, 1, OPENED_AT.plusMinutes(1));
        assertThatThrownBy(() -> cause.suppressBy(child, 1, OPENED_AT.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
        child.observe(
                KakaoUnlinkAlertType.DEAD_TASK,
                KakaoUnlinkAlertSeverity.WARNING,
                2,
                OPENED_AT.plusMinutes(2),
                deadDetails(),
                null,
                null);

        assertThat(child.getNotificationState()).isEqualTo(KakaoUnlinkNotificationState.SUPPRESSED);
        assertThat(child.getSuppressedByIncident()).isSameAs(cause);
        assertThat(child.getLastObservedAt()).isEqualTo(OPENED_AT.plusMinutes(2));
        assertThatThrownBy(() -> child.suppressBy(child, 1, OPENED_AT.plusMinutes(3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void observeFillsOnlyMissingReminderScheduleAndResolveClearsSuppression() {
        KakaoUnlinkIncident child = operationalIncident(1L, "KAKAO_UNLINK:DEAD_TASK:1:0");
        KakaoUnlinkIncident cause = operationalIncident(2L, "KAKAO_UNLINK:DEAD_TASK:2:0");
        LocalDateTime firstSchedule = OPENED_AT.plusMinutes(10);

        child.observe(
                KakaoUnlinkAlertType.DEAD_TASK,
                KakaoUnlinkAlertSeverity.WARNING,
                2,
                OPENED_AT.plusMinutes(1),
                deadDetails(),
                firstSchedule,
                null);
        child.observe(
                KakaoUnlinkAlertType.DEAD_TASK,
                KakaoUnlinkAlertSeverity.WARNING,
                3,
                OPENED_AT.plusMinutes(2),
                deadDetails(),
                OPENED_AT.plusMinutes(20),
                null);

        assertThat(child.getNextDiscordReminderAt()).isEqualTo(firstSchedule);
        child.suppressBy(cause, 1, OPENED_AT.plusMinutes(3));
        child.resolve(OPENED_AT.plusMinutes(4));

        assertThat(child.getNotificationState()).isEqualTo(KakaoUnlinkNotificationState.ELIGIBLE);
        assertThat(child.getSuppressedByIncident()).isNull();
        assertThat(child.getSuppressedByOccurrenceNo()).isNull();
        assertThat(child.getSuppressedAt()).isNull();
    }

    @Test
    void releaseSuppressionRequiresSuppressedOpenIncident() {
        KakaoUnlinkIncident incident = operationalIncident(1L, "KAKAO_UNLINK:DEAD_TASK:1:0");

        assertThatThrownBy(
                        () ->
                                incident.releaseSuppression(
                                        OPENED_AT.plusMinutes(2), OPENED_AT.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void syntheticIncidentRejectsOperationalLifecycleOperations() {
        KakaoUnlinkIncident synthetic = new KakaoUnlinkIncident();
        setCommonState(
                synthetic,
                1L,
                "KAKAO_UNLINK:SYNTHETIC_TEST",
                KakaoUnlinkAlertType.SYNTHETIC_TEST,
                new SyntheticTestSafeDetails());

        assertThatThrownBy(() -> synthetic.resolve(OPENED_AT.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                synthetic.observe(
                                        KakaoUnlinkAlertType.SYNTHETIC_TEST,
                                        KakaoUnlinkAlertSeverity.INFO,
                                        1,
                                        OPENED_AT.plusMinutes(1),
                                        new SyntheticTestSafeDetails(),
                                        null,
                                        null))
                .isInstanceOf(IllegalStateException.class);
    }

    private KakaoUnlinkIncident operationalIncident(long id, String fingerprint) {
        KakaoUnlinkIncident incident = new KakaoUnlinkIncident();
        setCommonState(incident, id, fingerprint, KakaoUnlinkAlertType.DEAD_TASK, deadDetails());
        return incident;
    }

    private void setCommonState(
            KakaoUnlinkIncident incident,
            long id,
            String fingerprint,
            KakaoUnlinkAlertType alertType,
            Object details) {
        ReflectionTestUtils.setField(incident, "id", id);
        ReflectionTestUtils.setField(incident, "fingerprint", fingerprint);
        ReflectionTestUtils.setField(incident, "alertType", alertType);
        ReflectionTestUtils.setField(incident, "severity", KakaoUnlinkAlertSeverity.WARNING);
        ReflectionTestUtils.setField(incident, "status", KakaoUnlinkIncidentStatus.OPEN);
        ReflectionTestUtils.setField(incident, "occurrenceNo", 1);
        ReflectionTestUtils.setField(incident, "severityEscalationNo", 0);
        ReflectionTestUtils.setField(incident, "openedAt", OPENED_AT);
        ReflectionTestUtils.setField(incident, "lastObservedAt", OPENED_AT);
        ReflectionTestUtils.setField(incident, "lastObservedScanSequence", 1L);
        ReflectionTestUtils.setField(
                incident, "notificationState", KakaoUnlinkNotificationState.ELIGIBLE);
        ReflectionTestUtils.setField(incident, "safeDetails", details);
        ReflectionTestUtils.setField(incident, "createdAt", OPENED_AT);
        ReflectionTestUtils.setField(incident, "updatedAt", OPENED_AT);
        ReflectionTestUtils.setField(incident, "version", 0L);
    }

    private DeadTaskSafeDetails deadDetails() {
        return new DeadTaskSafeDetails(
                1, 0, 1, KakaoUnlinkTaskStatus.DEAD, KakaoUnlinkTaskErrorType.REQUEST, 400, -1);
    }
}
