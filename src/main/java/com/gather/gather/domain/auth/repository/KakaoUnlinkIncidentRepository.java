package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncident;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncidentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoUnlinkIncidentRepository extends JpaRepository<KakaoUnlinkIncident, Long> {

    Optional<KakaoUnlinkIncident> findByFingerprint(String fingerprint);

    boolean existsByFingerprint(String fingerprint);

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    insert into kakao_unlink_incident (
                        fingerprint,
                        alert_type,
                        severity,
                        status,
                        occurrence_no,
                        severity_escalation_no,
                        opened_at,
                        last_observed_at,
                        last_observed_scan_sequence,
                        resolved_at,
                        notification_state,
                        next_discord_reminder_at,
                        next_email_reminder_at,
                        safe_details,
                        created_at,
                        updated_at,
                        version
                    ) values (
                        :fingerprint,
                        :alertType,
                        :severity,
                        'OPEN',
                        1,
                        0,
                        :observedAt,
                        :observedAt,
                        :scanSequence,
                        null,
                        'ELIGIBLE',
                        :nextDiscordReminderAt,
                        :nextEmailReminderAt,
                        cast(:safeDetails as json),
                        :observedAt,
                        :observedAt,
                        0
                    )
                    on duplicate key update
                        fingerprint = fingerprint
                    """,
            nativeQuery = true)
    int upsertInitialObservation(
            @Param("fingerprint") String fingerprint,
            @Param("alertType") String alertType,
            @Param("severity") String severity,
            @Param("observedAt") LocalDateTime observedAt,
            @Param("scanSequence") long scanSequence,
            @Param("nextDiscordReminderAt") LocalDateTime nextDiscordReminderAt,
            @Param("nextEmailReminderAt") LocalDateTime nextEmailReminderAt,
            @Param("safeDetails") String safeDetails);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select incident from KakaoUnlinkIncident incident where incident.fingerprint = :fingerprint")
    Optional<KakaoUnlinkIncident> findByFingerprintForUpdate(
            @Param("fingerprint") String fingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select incident from KakaoUnlinkIncident incident where incident.id = :id")
    Optional<KakaoUnlinkIncident> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select incident
            from KakaoUnlinkIncident incident
            where incident.id in :ids
            order by incident.id
            """)
    List<KakaoUnlinkIncident> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    long countByStatusAndAlertTypeNot(
            KakaoUnlinkIncidentStatus status, KakaoUnlinkAlertType excludedAlertType);

    default long countOperationalOpen() {
        return countByStatusAndAlertTypeNot(
                KakaoUnlinkIncidentStatus.OPEN, KakaoUnlinkAlertType.SYNTHETIC_TEST);
    }

    @Query(
            """
            select incident
            from KakaoUnlinkIncident incident
            where incident.status = :status
              and incident.alertType <> :excludedAlertType
            order by incident.id
            """)
    List<KakaoUnlinkIncident> findOperationalByStatus(
            @Param("status") KakaoUnlinkIncidentStatus status,
            @Param("excludedAlertType") KakaoUnlinkAlertType excludedAlertType);

    default List<KakaoUnlinkIncident> findOperationalOpen() {
        return findOperationalByStatus(
                KakaoUnlinkIncidentStatus.OPEN, KakaoUnlinkAlertType.SYNTHETIC_TEST);
    }

    @Query(
            """
            select incident
            from KakaoUnlinkIncident incident
            where incident.status = com.gather.gather.domain.auth.entity.KakaoUnlinkIncidentStatus.OPEN
              and incident.notificationState = com.gather.gather.domain.auth.entity.KakaoUnlinkNotificationState.ELIGIBLE
              and incident.alertType <> com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType.SYNTHETIC_TEST
              and (incident.notificationEligibleAt is null or incident.notificationEligibleAt <= :now)
              and (
                    incident.nextDiscordReminderAt <= :now
                 or incident.nextEmailReminderAt <= :now
              )
            order by incident.id
            """)
    List<KakaoUnlinkIncident> findOperationalReminderCandidates(@Param("now") LocalDateTime now);

    @Query(
            """
            select incident
            from KakaoUnlinkIncident incident
            where incident.status = com.gather.gather.domain.auth.entity.KakaoUnlinkIncidentStatus.OPEN
              and incident.notificationState = com.gather.gather.domain.auth.entity.KakaoUnlinkNotificationState.SUPPRESSED
              and (
                    incident.suppressedByIncident.status = com.gather.gather.domain.auth.entity.KakaoUnlinkIncidentStatus.RESOLVED
                 or incident.suppressedByIncident.occurrenceNo <> incident.suppressedByOccurrenceNo
              )
            order by incident.id
            """)
    List<KakaoUnlinkIncident> findStaleSuppressionCandidates();
}
