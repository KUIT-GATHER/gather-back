package com.gather.gather.domain.auth.kakao.monitoring.service;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertEventType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncident;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncidentStatus;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncidentTransition;
import com.gather.gather.domain.auth.entity.KakaoUnlinkMonitorControl;
import com.gather.gather.domain.auth.entity.KakaoUnlinkNotificationState;
import com.gather.gather.domain.auth.kakao.monitoring.exception.KakaoUnlinkMonitorLeaseLostException;
import com.gather.gather.domain.auth.kakao.monitoring.exception.KakaoUnlinkMonitoringInvariantException;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkAlertDeliveryResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentFingerprint;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentObservation;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentResolution;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSnapshot;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSuppression;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkMonitorLease;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkObservationResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkRecoveredDeliveryRequest;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkRecoveredDeliveryResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkReminderRequest;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkSuppressionRelease;
import com.gather.gather.domain.auth.kakao.monitoring.model.OperationalAlertPayloadSnapshot;
import com.gather.gather.domain.auth.repository.KakaoUnlinkIncidentRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkMonitorControlRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoUnlinkIncidentTransactionService {

    private final KakaoUnlinkMonitorControlRepository monitorControlRepository;
    private final KakaoUnlinkIncidentRepository incidentRepository;
    private final KakaoUnlinkAlertDeliveryPersistenceService deliveryPersistenceService;
    private final KakaoUnlinkMonitoringJsonCodec jsonCodec;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KakaoUnlinkObservationResult observe(KakaoUnlinkIncidentObservation observation) {
        LocalDateTime databaseNow = lockAndValidateLease(observation.lease());
        int inserted =
                incidentRepository.upsertInitialObservation(
                        observation.fingerprint().value(),
                        observation.alertType().name(),
                        observation.severity().name(),
                        databaseNow,
                        observation.lease().scanSequence(),
                        observation.nextDiscordReminderAt(),
                        observation.nextEmailReminderAt(),
                        jsonCodec.write(observation.safeDetails()));

        KakaoUnlinkIncident incident =
                incidentRepository
                        .findByFingerprintForUpdate(observation.fingerprint().value())
                        .orElseThrow(
                                () ->
                                        new KakaoUnlinkMonitoringInvariantException(
                                                "upsert한 incident를 다시 읽을 수 없습니다."));
        KakaoUnlinkIncidentTransition transition =
                incident.observe(
                        observation.alertType(),
                        observation.severity(),
                        observation.lease().scanSequence(),
                        databaseNow,
                        observation.safeDetails(),
                        observation.nextDiscordReminderAt(),
                        observation.nextEmailReminderAt());

        List<KakaoUnlinkAlertDeliveryResult> deliveryResults = new ArrayList<>();
        if (incident.getNotificationState() == KakaoUnlinkNotificationState.ELIGIBLE) {
            for (KakaoUnlinkAlertChannel channel : observation.initialChannels()) {
                OperationalAlertPayloadSnapshot payload =
                        snapshot(
                                incident,
                                KakaoUnlinkAlertEventType.INITIAL,
                                1,
                                channel,
                                databaseNow);
                deliveryResults.add(
                        deliveryPersistenceService.enqueueIfAbsent(
                                incident,
                                KakaoUnlinkAlertEventType.INITIAL,
                                1,
                                channel,
                                payload,
                                databaseNow));
            }
            if (transition.severityEscalated()) {
                for (KakaoUnlinkAlertChannel channel : observation.escalationChannels()) {
                    int sequence = incident.getSeverityEscalationNo();
                    OperationalAlertPayloadSnapshot payload =
                            snapshot(
                                    incident,
                                    KakaoUnlinkAlertEventType.ESCALATED,
                                    sequence,
                                    channel,
                                    databaseNow);
                    deliveryResults.add(
                            deliveryPersistenceService.enqueueExact(
                                    incident,
                                    KakaoUnlinkAlertEventType.ESCALATED,
                                    sequence,
                                    channel,
                                    payload,
                                    databaseNow));
                }
            }
        }
        if (inserted == 1 || transition.reopened() || transition.severityEscalated()) {
            log.info(
                    "Kakao unlink incident를 조정했습니다: incidentId={}, occurrenceNo={}, inserted={}, reopened={}, severityEscalated={}, deliveryCount={}",
                    incident.getId(),
                    incident.getOccurrenceNo(),
                    inserted == 1,
                    transition.reopened(),
                    transition.severityEscalated(),
                    deliveryResults.size());
        }
        return new KakaoUnlinkObservationResult(
                KakaoUnlinkIncidentSnapshot.from(incident), transition, deliveryResults);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KakaoUnlinkIncidentSnapshot resolve(KakaoUnlinkIncidentResolution resolution) {
        LocalDateTime databaseNow = lockAndValidateLease(resolution.lease());
        KakaoUnlinkIncident incident = findIncidentForUpdate(resolution.incidentId());
        incident.resolve(databaseNow);
        return KakaoUnlinkIncidentSnapshot.from(incident);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KakaoUnlinkIncidentSnapshot suppress(KakaoUnlinkIncidentSuppression suppression) {
        LocalDateTime databaseNow = lockAndValidateLease(suppression.lease());
        List<Long> ids =
                List.of(suppression.incidentId(), suppression.suppressingIncidentId()).stream()
                        .sorted()
                        .toList();
        List<KakaoUnlinkIncident> locked = incidentRepository.findAllByIdForUpdate(ids);
        if (locked.size() != 2) {
            throw new KakaoUnlinkMonitoringInvariantException(
                    "suppression 대상 incident를 모두 찾을 수 없습니다.");
        }
        KakaoUnlinkIncident child = findById(locked, suppression.incidentId());
        KakaoUnlinkIncident cause = findById(locked, suppression.suppressingIncidentId());
        child.suppressBy(cause, suppression.suppressingOccurrenceNo(), databaseNow);
        return KakaoUnlinkIncidentSnapshot.from(child);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KakaoUnlinkIncidentSnapshot releaseSuppression(KakaoUnlinkSuppressionRelease release) {
        LocalDateTime databaseNow = lockAndValidateLease(release.lease());
        KakaoUnlinkIncident incident = findIncidentForUpdate(release.incidentId());
        incident.releaseSuppression(release.eligibleAt(), databaseNow);
        return KakaoUnlinkIncidentSnapshot.from(incident);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KakaoUnlinkAlertDeliveryResult recordReminder(KakaoUnlinkReminderRequest request) {
        LocalDateTime databaseNow = lockAndValidateLease(request.lease());
        KakaoUnlinkIncident incident = findIncidentForUpdate(request.incidentId());
        int sequence = deliveryPersistenceService.nextReminderSequence(incident, request.channel());
        incident.scheduleNextReminder(request.channel(), request.nextReminderAt(), databaseNow);
        OperationalAlertPayloadSnapshot payload =
                snapshot(
                        incident,
                        KakaoUnlinkAlertEventType.REMINDER,
                        sequence,
                        request.channel(),
                        databaseNow);
        return deliveryPersistenceService.enqueueExact(
                incident,
                KakaoUnlinkAlertEventType.REMINDER,
                sequence,
                request.channel(),
                payload,
                databaseNow);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KakaoUnlinkRecoveredDeliveryResult enqueueRecovered(
            KakaoUnlinkRecoveredDeliveryRequest request) {
        LocalDateTime databaseNow = lockAndValidateLease(request.lease());
        KakaoUnlinkIncident incident = findIncidentForUpdate(request.incidentId());
        if (incident.isSynthetic()
                || incident.getStatus() != KakaoUnlinkIncidentStatus.RESOLVED
                || incident.getOccurrenceNo() != request.occurrenceNo()) {
            return KakaoUnlinkRecoveredDeliveryResult.notApplicable();
        }
        if (!deliveryPersistenceService.hasSuccessfulProblemDelivery(
                incident.getId(), incident.getOccurrenceNo(), request.channel())) {
            return KakaoUnlinkRecoveredDeliveryResult.noSuccessfulProblemAlert();
        }
        OperationalAlertPayloadSnapshot payload =
                snapshot(
                        incident,
                        KakaoUnlinkAlertEventType.RECOVERED,
                        1,
                        request.channel(),
                        databaseNow);
        return KakaoUnlinkRecoveredDeliveryResult.enqueued(
                deliveryPersistenceService.enqueueIfAbsent(
                        incident,
                        KakaoUnlinkAlertEventType.RECOVERED,
                        1,
                        request.channel(),
                        payload,
                        databaseNow));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<KakaoUnlinkAlertDeliveryResult> enqueueSyntheticTest(
            Set<KakaoUnlinkAlertChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("synthetic test channel은 하나 이상이어야 합니다.");
        }
        // Synthetic test is an explicit operator action, not a monitor scan, so it has no scan
        // lease.
        LocalDateTime databaseNow = monitorControlRepository.currentUtcDateTime();
        KakaoUnlinkIncident incident =
                incidentRepository
                        .findByFingerprintForUpdate(
                                KakaoUnlinkIncidentFingerprint.SYNTHETIC_TEST_VALUE)
                        .orElseThrow(
                                () ->
                                        new KakaoUnlinkMonitoringInvariantException(
                                                "synthetic singleton incident가 없습니다."));
        if (!incident.isSynthetic()
                || incident.getOccurrenceNo() != 1
                || incident.getStatus() != KakaoUnlinkIncidentStatus.OPEN) {
            throw new KakaoUnlinkMonitoringInvariantException(
                    "synthetic singleton incident 상태가 올바르지 않습니다.");
        }
        int sequence = deliveryPersistenceService.nextSyntheticTestSequence(incident);
        List<KakaoUnlinkAlertDeliveryResult> results = new ArrayList<>(channels.size());
        channels.stream()
                .sorted(Comparator.comparing(Enum::name))
                .forEach(
                        channel -> {
                            OperationalAlertPayloadSnapshot payload =
                                    snapshot(
                                            incident,
                                            KakaoUnlinkAlertEventType.TEST,
                                            sequence,
                                            channel,
                                            databaseNow);
                            results.add(
                                    deliveryPersistenceService.enqueueExact(
                                            incident,
                                            KakaoUnlinkAlertEventType.TEST,
                                            sequence,
                                            channel,
                                            payload,
                                            databaseNow));
                        });
        return List.copyOf(results);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean hasSuccessfulProblemDelivery(
            long incidentId, int occurrenceNo, KakaoUnlinkAlertChannel channel) {
        if (incidentId <= 0 || occurrenceNo < 1 || channel == null) {
            throw new IllegalArgumentException("delivery 성공 이력 조회 값이 올바르지 않습니다.");
        }
        return deliveryPersistenceService.hasSuccessfulProblemDelivery(
                incidentId, occurrenceNo, channel);
    }

    private LocalDateTime lockAndValidateLease(KakaoUnlinkMonitorLease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("monitor lease는 필수입니다.");
        }
        KakaoUnlinkMonitorControl control =
                monitorControlRepository
                        .findSingletonForUpdate()
                        .orElseThrow(
                                () ->
                                        new KakaoUnlinkMonitoringInvariantException(
                                                "Kakao unlink monitor control이 없습니다."));
        LocalDateTime databaseNow = monitorControlRepository.currentUtcDateTime();
        if (!control.hasOwnedValidLease(
                lease.scanSequence(), lease.owner(), lease.token(), databaseNow)) {
            log.warn(
                    "Kakao unlink monitoring mutation이 stale lease로 fencing되었습니다: sequence={}, owner={}",
                    lease.scanSequence(),
                    lease.owner());
            throw new KakaoUnlinkMonitorLeaseLostException();
        }
        return databaseNow;
    }

    private KakaoUnlinkIncident findIncidentForUpdate(long incidentId) {
        return incidentRepository
                .findByIdForUpdate(incidentId)
                .orElseThrow(
                        () ->
                                new KakaoUnlinkMonitoringInvariantException(
                                        "Kakao unlink incident를 찾을 수 없습니다."));
    }

    private KakaoUnlinkIncident findById(List<KakaoUnlinkIncident> incidents, long id) {
        return incidents.stream()
                .filter(incident -> incident.getId() == id)
                .findFirst()
                .orElseThrow(
                        () ->
                                new KakaoUnlinkMonitoringInvariantException(
                                        "잠근 incident 목록에서 대상을 찾을 수 없습니다."));
    }

    private OperationalAlertPayloadSnapshot snapshot(
            KakaoUnlinkIncident incident,
            KakaoUnlinkAlertEventType eventType,
            int eventSequence,
            KakaoUnlinkAlertChannel channel,
            LocalDateTime databaseNow) {
        Instant generatedAt = databaseNow.toInstant(ZoneOffset.UTC);
        return new OperationalAlertPayloadSnapshot(
                OperationalAlertPayloadSnapshot.CURRENT_SCHEMA_VERSION,
                incident.getFingerprint(),
                incident.getAlertType(),
                incident.getOccurrenceNo(),
                incident.getSeverity(),
                eventType,
                eventSequence,
                channel,
                generatedAt,
                incident.getSafeDetails());
    }
}
