package com.gather.gather.domain.auth.kakao.monitoring.service;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertDelivery;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertDeliveryStatus;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertEventType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncident;
import com.gather.gather.domain.auth.kakao.monitoring.exception.KakaoUnlinkMonitoringInvariantException;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkAlertDeliveryResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.OperationalAlertPayloadSnapshot;
import com.gather.gather.domain.auth.repository.KakaoUnlinkAlertDeliveryRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkIncidentRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class KakaoUnlinkAlertDeliveryPersistenceService {

    private static final EnumSet<KakaoUnlinkAlertEventType> PROBLEM_EVENTS =
            EnumSet.of(
                    KakaoUnlinkAlertEventType.INITIAL,
                    KakaoUnlinkAlertEventType.REMINDER,
                    KakaoUnlinkAlertEventType.ESCALATED);

    private final KakaoUnlinkAlertDeliveryRepository deliveryRepository;
    private final KakaoUnlinkIncidentRepository incidentRepository;
    private final KakaoUnlinkMonitoringJsonCodec jsonCodec;

    public KakaoUnlinkAlertDeliveryResult enqueueIfAbsent(
            KakaoUnlinkIncident incident,
            KakaoUnlinkAlertEventType eventType,
            int eventSequence,
            KakaoUnlinkAlertChannel channel,
            OperationalAlertPayloadSnapshot payload,
            LocalDateTime databaseNow) {
        validateEvent(incident, eventType, eventSequence, channel, payload, databaseNow);
        KakaoUnlinkAlertDelivery existing =
                findExisting(incident, eventType, eventSequence, channel);
        if (existing != null) {
            if (!existing.getPayloadSnapshot().hasSameLogicalContent(payload)) {
                throw new KakaoUnlinkMonitoringInvariantException(
                        "동일 delivery 자연키에 다른 payload가 요청되었습니다.");
            }
            log.debug(
                    "Kakao unlink alert delivery 중복 생성을 건너뜁니다: incidentId={}, occurrenceNo={}, eventType={}, eventSequence={}, channel={}",
                    incident.getId(),
                    incident.getOccurrenceNo(),
                    eventType,
                    eventSequence,
                    channel);
            return new KakaoUnlinkAlertDeliveryResult(
                    existing.getId(), existing.getEventSequence(), false);
        }
        return insertAndLoad(incident, eventType, eventSequence, channel, payload, databaseNow);
    }

    public KakaoUnlinkAlertDeliveryResult enqueueExact(
            KakaoUnlinkIncident incident,
            KakaoUnlinkAlertEventType eventType,
            int eventSequence,
            KakaoUnlinkAlertChannel channel,
            OperationalAlertPayloadSnapshot payload,
            LocalDateTime databaseNow) {
        validateEvent(incident, eventType, eventSequence, channel, payload, databaseNow);
        KakaoUnlinkAlertDelivery existing =
                findExisting(incident, eventType, eventSequence, channel);
        if (existing != null) {
            if (!existing.getPayloadSnapshot().hasSameLogicalContent(payload)) {
                throw new KakaoUnlinkMonitoringInvariantException(
                        "동일 delivery 자연키에 다른 payload가 요청되었습니다.");
            }
            return new KakaoUnlinkAlertDeliveryResult(
                    existing.getId(), existing.getEventSequence(), false);
        }
        return insertAndLoad(incident, eventType, eventSequence, channel, payload, databaseNow);
    }

    public int nextReminderSequence(KakaoUnlinkIncident incident, KakaoUnlinkAlertChannel channel) {
        Integer maximum =
                deliveryRepository.findMaximumReminderSequence(
                        incident.getId(), incident.getOccurrenceNo(), channel);
        return Math.addExact(maximum == null ? 0 : maximum, 1);
    }

    public int nextSyntheticTestSequence(KakaoUnlinkIncident incident) {
        Integer maximum = deliveryRepository.findMaximumSyntheticTestSequence(incident.getId());
        return Math.addExact(maximum == null ? 0 : maximum, 1);
    }

    public boolean hasSuccessfulProblemDelivery(
            long incidentId, int occurrenceNo, KakaoUnlinkAlertChannel channel) {
        return deliveryRepository
                .existsByIncidentIdAndOccurrenceNoAndChannelAndStatusAndEventTypeIn(
                        incidentId,
                        occurrenceNo,
                        channel,
                        KakaoUnlinkAlertDeliveryStatus.SUCCEEDED,
                        PROBLEM_EVENTS);
    }

    private KakaoUnlinkAlertDeliveryResult insertAndLoad(
            KakaoUnlinkIncident incident,
            KakaoUnlinkAlertEventType eventType,
            int eventSequence,
            KakaoUnlinkAlertChannel channel,
            OperationalAlertPayloadSnapshot payload,
            LocalDateTime databaseNow) {
        int affected =
                deliveryRepository.upsertPending(
                        incident.getId(),
                        incident.getOccurrenceNo(),
                        eventType.name(),
                        eventSequence,
                        channel.name(),
                        jsonCodec.write(payload),
                        databaseNow,
                        databaseNow);
        if (affected != 1) {
            throw new KakaoUnlinkMonitoringInvariantException(
                    "delivery insert가 incident lock 안에서 정확히 한 행을 생성하지 못했습니다.");
        }
        KakaoUnlinkAlertDelivery persisted =
                deliveryRepository
                        .findEventForUpdate(
                                incident.getId(),
                                incident.getOccurrenceNo(),
                                eventType,
                                eventSequence,
                                channel)
                        .orElseThrow(
                                () ->
                                        new KakaoUnlinkMonitoringInvariantException(
                                                "upsert한 delivery를 다시 읽을 수 없습니다."));
        if (!persisted.getPayloadSnapshot().hasSameLogicalContent(payload)) {
            throw new KakaoUnlinkMonitoringInvariantException(
                    "동일 delivery 자연키가 다른 payload를 가지고 있습니다.");
        }
        return new KakaoUnlinkAlertDeliveryResult(
                persisted.getId(), persisted.getEventSequence(), true);
    }

    private KakaoUnlinkAlertDelivery findExisting(
            KakaoUnlinkIncident incident,
            KakaoUnlinkAlertEventType eventType,
            int eventSequence,
            KakaoUnlinkAlertChannel channel) {
        return deliveryRepository
                .findEventForUpdate(
                        incident.getId(),
                        incident.getOccurrenceNo(),
                        eventType,
                        eventSequence,
                        channel)
                .orElse(null);
    }

    private void validateEvent(
            KakaoUnlinkIncident incident,
            KakaoUnlinkAlertEventType eventType,
            int eventSequence,
            KakaoUnlinkAlertChannel channel,
            OperationalAlertPayloadSnapshot payload,
            LocalDateTime databaseNow) {
        validateKey(incident, eventType, eventSequence, channel);
        if (payload == null || databaseNow == null) {
            throw new IllegalArgumentException("delivery payload와 생성 시각은 필수입니다.");
        }
        if (!payload.fingerprint().equals(incident.getFingerprint())
                || payload.alertType() != incident.getAlertType()
                || payload.occurrenceNo() != incident.getOccurrenceNo()
                || payload.severity() != incident.getSeverity()
                || payload.eventType() != eventType
                || payload.eventSequence() != eventSequence
                || payload.channel() != channel) {
            throw new KakaoUnlinkMonitoringInvariantException(
                    "delivery payload가 incident/event 자연키와 일치하지 않습니다.");
        }
    }

    private void validateKey(
            KakaoUnlinkIncident incident,
            KakaoUnlinkAlertEventType eventType,
            int eventSequence,
            KakaoUnlinkAlertChannel channel) {
        if (incident == null || incident.getId() == null || eventType == null || channel == null) {
            throw new IllegalArgumentException("delivery 자연키 필수 값이 누락되었습니다.");
        }
        KakaoUnlinkIncident locked =
                incidentRepository
                        .findByIdForUpdate(incident.getId())
                        .orElseThrow(
                                () ->
                                        new KakaoUnlinkMonitoringInvariantException(
                                                "delivery 대상 incident를 잠글 수 없습니다."));
        if (locked.getOccurrenceNo() != incident.getOccurrenceNo()) {
            throw new KakaoUnlinkMonitoringInvariantException(
                    "delivery 대상 incident occurrence가 변경되었습니다.");
        }
        eventType.validateSequence(eventSequence);
        if (incident.isSynthetic() != (eventType == KakaoUnlinkAlertEventType.TEST)) {
            throw new KakaoUnlinkMonitoringInvariantException(
                    "synthetic incident에는 TEST delivery만 허용됩니다.");
        }
    }
}
