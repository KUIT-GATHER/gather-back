package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertDelivery;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertEventType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoUnlinkAlertDeliveryRepository
        extends JpaRepository<KakaoUnlinkAlertDelivery, Long> {

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    insert into kakao_unlink_alert_delivery (
                        incident_id,
                        occurrence_no,
                        event_type,
                        event_sequence,
                        channel,
                        status,
                        payload_snapshot,
                        attempt_count,
                        available_at,
                        created_at,
                        updated_at,
                        version
                    ) values (
                        :incidentId,
                        :occurrenceNo,
                        :eventType,
                        :eventSequence,
                        :channel,
                        'PENDING',
                        cast(:payloadSnapshot as json),
                        0,
                        :availableAt,
                        :createdAt,
                        :createdAt,
                        0
                    )
                    on duplicate key update
                        id = id
                    """,
            nativeQuery = true)
    int upsertPending(
            @Param("incidentId") Long incidentId,
            @Param("occurrenceNo") int occurrenceNo,
            @Param("eventType") String eventType,
            @Param("eventSequence") int eventSequence,
            @Param("channel") String channel,
            @Param("payloadSnapshot") String payloadSnapshot,
            @Param("availableAt") LocalDateTime availableAt,
            @Param("createdAt") LocalDateTime createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select delivery
            from KakaoUnlinkAlertDelivery delivery
            where delivery.incident.id = :incidentId
              and delivery.occurrenceNo = :occurrenceNo
              and delivery.eventType = :eventType
              and delivery.eventSequence = :eventSequence
              and delivery.channel = :channel
            """)
    Optional<KakaoUnlinkAlertDelivery> findEventForUpdate(
            @Param("incidentId") Long incidentId,
            @Param("occurrenceNo") int occurrenceNo,
            @Param("eventType") KakaoUnlinkAlertEventType eventType,
            @Param("eventSequence") int eventSequence,
            @Param("channel") KakaoUnlinkAlertChannel channel);

    @Query(
            """
            select max(delivery.eventSequence)
            from KakaoUnlinkAlertDelivery delivery
            where delivery.incident.id = :incidentId
              and delivery.occurrenceNo = :occurrenceNo
              and delivery.eventType = com.gather.gather.domain.auth.entity.KakaoUnlinkAlertEventType.REMINDER
              and delivery.channel = :channel
            """)
    Integer findMaximumReminderSequence(
            @Param("incidentId") Long incidentId,
            @Param("occurrenceNo") int occurrenceNo,
            @Param("channel") KakaoUnlinkAlertChannel channel);

    @Query(
            """
            select max(delivery.eventSequence)
            from KakaoUnlinkAlertDelivery delivery
            where delivery.incident.id = :incidentId
              and delivery.eventType = com.gather.gather.domain.auth.entity.KakaoUnlinkAlertEventType.TEST
            """)
    Integer findMaximumSyntheticTestSequence(@Param("incidentId") Long incidentId);

    boolean existsByIncidentIdAndOccurrenceNoAndChannelAndStatusAndEventTypeIn(
            Long incidentId,
            int occurrenceNo,
            KakaoUnlinkAlertChannel channel,
            com.gather.gather.domain.auth.entity.KakaoUnlinkAlertDeliveryStatus status,
            Collection<KakaoUnlinkAlertEventType> eventTypes);

    @Query(value = "select UTC_TIMESTAMP(6)", nativeQuery = true)
    Instant currentUtcInstant();

    default LocalDateTime currentUtcDateTime() {
        return LocalDateTime.ofInstant(currentUtcInstant(), ZoneOffset.UTC);
    }
}
