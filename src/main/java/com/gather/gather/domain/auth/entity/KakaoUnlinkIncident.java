package com.gather.gather.domain.auth.entity;

import com.gather.gather.domain.auth.kakao.monitoring.exception.KakaoUnlinkMonitoringInvariantException;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentFingerprint;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSafeDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "kakao_unlink_incident")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoUnlinkIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 191, updatable = false)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private KakaoUnlinkAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KakaoUnlinkAlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KakaoUnlinkIncidentStatus status;

    @Column(nullable = false)
    private int occurrenceNo;

    @Column(nullable = false)
    private int severityEscalationNo;

    @Column(nullable = false)
    private LocalDateTime openedAt;

    @Column(nullable = false)
    private LocalDateTime lastObservedAt;

    @Column(nullable = false)
    private long lastObservedScanSequence;

    @Column private LocalDateTime resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KakaoUnlinkNotificationState notificationState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suppressed_by_incident_id")
    private KakaoUnlinkIncident suppressedByIncident;

    @Column private Integer suppressedByOccurrenceNo;

    @Column private LocalDateTime suppressedAt;

    @Column private LocalDateTime notificationEligibleAt;

    @Column private LocalDateTime nextDiscordReminderAt;

    @Column private LocalDateTime nextEmailReminderAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false)
    private KakaoUnlinkIncidentSafeDetails safeDetails;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public boolean isSynthetic() {
        return alertType == KakaoUnlinkAlertType.SYNTHETIC_TEST;
    }

    @PostLoad
    private void validatePersistedFingerprint() {
        try {
            KakaoUnlinkIncidentFingerprint.validateStored(fingerprint, alertType);
        } catch (IllegalArgumentException exception) {
            throw new KakaoUnlinkMonitoringInvariantException(
                    "저장된 incident fingerprint와 alert type이 일치하지 않습니다.", exception);
        }
    }

    public boolean isOpen() {
        return status == KakaoUnlinkIncidentStatus.OPEN;
    }

    public KakaoUnlinkIncidentTransition observe(
            KakaoUnlinkAlertType observedType,
            KakaoUnlinkAlertSeverity observedSeverity,
            long scanSequence,
            LocalDateTime observedAt,
            KakaoUnlinkIncidentSafeDetails observedDetails,
            LocalDateTime nextDiscordReminderAt,
            LocalDateTime nextEmailReminderAt) {
        validateObservation(
                observedType, observedSeverity, scanSequence, observedAt, observedDetails);
        if (isSynthetic()) {
            throw new IllegalStateException("synthetic incident는 detector observation 대상이 아닙니다.");
        }

        boolean reopened = status == KakaoUnlinkIncidentStatus.RESOLVED;
        boolean escalated = false;
        if (reopened) {
            occurrenceNo = Math.addExact(occurrenceNo, 1);
            severityEscalationNo = 0;
            severity = observedSeverity;
            status = KakaoUnlinkIncidentStatus.OPEN;
            openedAt = observedAt;
            resolvedAt = null;
            notificationState = KakaoUnlinkNotificationState.ELIGIBLE;
            clearSuppression();
            notificationEligibleAt = null;
            this.nextDiscordReminderAt = nextDiscordReminderAt;
            this.nextEmailReminderAt = nextEmailReminderAt;
        } else if (observedSeverity.isHigherThan(severity)) {
            severity = observedSeverity;
            severityEscalationNo = Math.addExact(severityEscalationNo, 1);
            escalated = true;
        }
        if (this.nextDiscordReminderAt == null && nextDiscordReminderAt != null) {
            this.nextDiscordReminderAt = nextDiscordReminderAt;
        }
        if (this.nextEmailReminderAt == null && nextEmailReminderAt != null) {
            this.nextEmailReminderAt = nextEmailReminderAt;
        }

        if (scanSequence > lastObservedScanSequence) {
            lastObservedScanSequence = scanSequence;
            safeDetails = observedDetails;
        }
        if (observedAt.isAfter(lastObservedAt)) {
            lastObservedAt = observedAt;
        }
        updatedAt = observedAt.isAfter(updatedAt) ? observedAt : updatedAt;
        return new KakaoUnlinkIncidentTransition(reopened, escalated);
    }

    public void resolve(LocalDateTime now) {
        requireNow(now);
        rejectSynthetic("resolve");
        if (status == KakaoUnlinkIncidentStatus.RESOLVED) {
            return;
        }
        status = KakaoUnlinkIncidentStatus.RESOLVED;
        resolvedAt = now;
        notificationState = KakaoUnlinkNotificationState.ELIGIBLE;
        clearSuppression();
        notificationEligibleAt = null;
        nextDiscordReminderAt = null;
        nextEmailReminderAt = null;
        updatedAt = now;
    }

    public void suppressBy(KakaoUnlinkIncident cause, int causeOccurrenceNo, LocalDateTime now) {
        requireNow(now);
        rejectSynthetic("suppression");
        if (cause == null || cause.id == null || cause.id.equals(id) || cause.isSynthetic()) {
            throw new IllegalArgumentException(
                    "유효한 별도 operational incident만 suppression 원인이 될 수 있습니다.");
        }
        if (!isOpen()
                || !cause.isOpen()
                || cause.notificationState != KakaoUnlinkNotificationState.ELIGIBLE
                || cause.occurrenceNo != causeOccurrenceNo) {
            throw new IllegalStateException("현재 OPEN occurrence만 suppression 관계를 만들 수 있습니다.");
        }
        notificationState = KakaoUnlinkNotificationState.SUPPRESSED;
        suppressedByIncident = cause;
        suppressedByOccurrenceNo = causeOccurrenceNo;
        suppressedAt = now;
        notificationEligibleAt = null;
        updatedAt = now;
    }

    public void releaseSuppression(LocalDateTime eligibleAt, LocalDateTime now) {
        requireNow(now);
        rejectSynthetic("suppression release");
        if (!isOpen() || notificationState != KakaoUnlinkNotificationState.SUPPRESSED) {
            throw new IllegalStateException("억제 중인 OPEN incident만 suppression을 해제할 수 있습니다.");
        }
        if (eligibleAt != null && eligibleAt.isBefore(now)) {
            throw new IllegalArgumentException("notification eligible 시각은 현재보다 빠를 수 없습니다.");
        }
        notificationState = KakaoUnlinkNotificationState.ELIGIBLE;
        clearSuppression();
        notificationEligibleAt = eligibleAt;
        updatedAt = now;
    }

    public void scheduleNextReminder(
            KakaoUnlinkAlertChannel channel, LocalDateTime nextReminderAt, LocalDateTime now) {
        requireNow(now);
        rejectSynthetic("reminder");
        if (!isOpen() || notificationState != KakaoUnlinkNotificationState.ELIGIBLE) {
            throw new IllegalStateException("알림 가능한 OPEN incident만 reminder를 기록할 수 있습니다.");
        }
        if (nextReminderAt != null && nextReminderAt.isBefore(now)) {
            throw new IllegalArgumentException("다음 reminder 시각은 현재보다 빠를 수 없습니다.");
        }
        if (channel == null) {
            throw new IllegalArgumentException("알림 channel은 필수입니다.");
        }
        switch (channel) {
            case DISCORD -> nextDiscordReminderAt = nextReminderAt;
            case EMAIL -> nextEmailReminderAt = nextReminderAt;
        }
        updatedAt = now;
    }

    private void validateObservation(
            KakaoUnlinkAlertType observedType,
            KakaoUnlinkAlertSeverity observedSeverity,
            long scanSequence,
            LocalDateTime observedAt,
            KakaoUnlinkIncidentSafeDetails observedDetails) {
        requireNow(observedAt);
        if (observedType == null
                || observedSeverity == null
                || observedDetails == null
                || scanSequence < 1) {
            throw new IllegalArgumentException("incident observation 값이 올바르지 않습니다.");
        }
        if (alertType != observedType) {
            throw new IllegalStateException("fingerprint와 alert type이 충돌합니다.");
        }
        if (!observedDetails.supports(observedType)) {
            throw new IllegalArgumentException("alert type과 safe details 종류가 일치하지 않습니다.");
        }
    }

    private void clearSuppression() {
        suppressedByIncident = null;
        suppressedByOccurrenceNo = null;
        suppressedAt = null;
    }

    private void rejectSynthetic(String operation) {
        if (isSynthetic()) {
            throw new IllegalStateException("synthetic incident는 " + operation + " 대상이 아닙니다.");
        }
    }

    private static void requireNow(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("incident 변경 시각은 필수입니다.");
        }
    }
}
