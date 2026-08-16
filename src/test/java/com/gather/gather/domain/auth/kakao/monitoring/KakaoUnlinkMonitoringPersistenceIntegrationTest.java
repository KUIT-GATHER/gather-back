package com.gather.gather.domain.auth.kakao.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertEventType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertSeverity;
import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncident;
import com.gather.gather.domain.auth.entity.KakaoUnlinkIncidentStatus;
import com.gather.gather.domain.auth.entity.KakaoUnlinkNotificationState;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskErrorType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus;
import com.gather.gather.domain.auth.kakao.monitoring.exception.KakaoUnlinkMonitorLeaseLostException;
import com.gather.gather.domain.auth.kakao.monitoring.exception.KakaoUnlinkMonitoringInvariantException;
import com.gather.gather.domain.auth.kakao.monitoring.model.DeadTaskSafeDetails;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentFingerprint;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentObservation;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentResolution;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSnapshot;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkIncidentSuppression;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkMonitorLease;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkMonitorLeaseAcquireResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkMonitorLeaseFinishResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkObservationResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkRecoveredDeliveryRequest;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkRecoveredDeliveryResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkReminderRequest;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkSuppressionRelease;
import com.gather.gather.domain.auth.kakao.monitoring.model.OperationalAlertPayloadSnapshot;
import com.gather.gather.domain.auth.kakao.monitoring.model.TaskPopulationSafeDetails;
import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkAlertDeliveryPersistenceService;
import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkIncidentReconciliationService;
import com.gather.gather.domain.auth.kakao.monitoring.service.KakaoUnlinkMonitorLeaseService;
import com.gather.gather.domain.auth.repository.KakaoUnlinkAlertDeliveryRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkIncidentRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkMonitorControlRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkWorkerControlRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class KakaoUnlinkMonitoringPersistenceIntegrationTest {

    private static final AtomicLong FINGERPRINT_SEQUENCE = new AtomicLong();

    @Autowired private KakaoUnlinkMonitorLeaseService leaseService;
    @Autowired private KakaoUnlinkIncidentReconciliationService reconciliationService;
    @Autowired private KakaoUnlinkAlertDeliveryPersistenceService deliveryPersistenceService;
    @Autowired private KakaoUnlinkMonitorControlRepository monitorControlRepository;
    @Autowired private KakaoUnlinkWorkerControlRepository workerControlRepository;
    @Autowired private KakaoUnlinkIncidentRepository incidentRepository;
    @Autowired private KakaoUnlinkAlertDeliveryRepository deliveryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void migrationCreatesSingletonsAndNullableHeartbeatStorage() {
        assertThat(monitorControlRepository.findById(1L)).isPresent();
        assertThat(workerControlRepository.findById(1L)).isPresent();
        var workerControl = workerControlRepository.findById(1L).orElseThrow();
        assertThat(workerControl.getLastPollStartedAt()).isNull();
        assertThat(workerControl.getLastPollCompletedAt()).isNull();
        assertThat(workerControl.getLastPollFailedAt()).isNull();
        assertThat(workerControl.getLastPollFailureType()).isNull();

        jdbcTemplate.update(
                "update kakao_unlink_worker_control set last_poll_started_at = UTC_TIMESTAMP(6), last_poll_completed_at = UTC_TIMESTAMP(6), last_poll_failed_at = UTC_TIMESTAMP(6), last_poll_failure_type = 'DATABASE' where id = 1");
        workerControl = workerControlRepository.findById(1L).orElseThrow();
        assertThat(workerControl.getLastPollStartedAt()).isNotNull();
        assertThat(workerControl.getLastPollCompletedAt()).isNotNull();
        assertThat(workerControl.getLastPollFailedAt()).isNotNull();
        assertThat(workerControl.getLastPollFailureType()).isEqualTo("DATABASE");

        Integer migrationCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from flyway_schema_history where description = 'add kakao unlink monitoring persistence' and success = 1",
                        Integer.class);
        assertThat(migrationCount).isEqualTo(1);

        Integer naturalKeyCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from information_schema.table_constraints where constraint_schema = database() and constraint_name in ('uk_kakao_unlink_incident_fingerprint', 'uk_kakao_unlink_alert_delivery_event')",
                        Integer.class);
        assertThat(naturalKeyCount).isEqualTo(2);

        KakaoUnlinkIncident synthetic =
                incidentRepository
                        .findByFingerprint(KakaoUnlinkIncidentFingerprint.SYNTHETIC_TEST_VALUE)
                        .orElseThrow();
        assertThat(synthetic.getAlertType()).isEqualTo(KakaoUnlinkAlertType.SYNTHETIC_TEST);
        assertThat(synthetic.getStatus()).isEqualTo(KakaoUnlinkIncidentStatus.OPEN);
        assertThat(synthetic.getOccurrenceNo()).isEqualTo(1);
    }

    @Test
    void concurrentScanLeaseAcquireHasExactlyOneWinner() throws Exception {
        List<KakaoUnlinkMonitorLeaseAcquireResult> attempts =
                runConcurrently(
                        () ->
                                leaseService.tryAcquire(
                                        "monitor-" + Thread.currentThread().getName(),
                                        Duration.ofMinutes(3)));

        assertThat(attempts)
                .extracting(KakaoUnlinkMonitorLeaseAcquireResult::outcome)
                .containsExactlyInAnyOrder(
                        KakaoUnlinkMonitorLeaseAcquireResult.Outcome.ACQUIRED,
                        KakaoUnlinkMonitorLeaseAcquireResult.Outcome.BUSY);
        KakaoUnlinkMonitorLease winner =
                attempts.stream()
                        .map(KakaoUnlinkMonitorLeaseAcquireResult::lease)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElseThrow();
        assertThat(leaseService.complete(winner))
                .isEqualTo(KakaoUnlinkMonitorLeaseFinishResult.COMPLETED);
    }

    @Test
    void scanLeaseSerializesReclaimsAndFencesStaleOwner() throws InterruptedException {
        KakaoUnlinkMonitorLeaseAcquireResult firstResult =
                leaseService.tryAcquire("monitor-a", Duration.ofMillis(500));
        assertThat(firstResult.outcome())
                .isEqualTo(KakaoUnlinkMonitorLeaseAcquireResult.Outcome.ACQUIRED);
        KakaoUnlinkMonitorLease first = firstResult.lease();
        assertThat(leaseService.tryAcquire("monitor-b", Duration.ofMinutes(3)).outcome())
                .isEqualTo(KakaoUnlinkMonitorLeaseAcquireResult.Outcome.BUSY);

        Thread.sleep(750);
        KakaoUnlinkMonitorLease second = acquire("monitor-b");

        assertThat(second.scanSequence()).isEqualTo(first.scanSequence() + 1);
        assertThat(leaseService.complete(first))
                .isEqualTo(KakaoUnlinkMonitorLeaseFinishResult.LEASE_LOST);
        assertThat(
                        leaseService.fail(
                                first,
                                com.gather.gather.domain.auth.kakao.monitoring.model
                                        .KakaoUnlinkOperationalFailureType.TIMEOUT))
                .isEqualTo(KakaoUnlinkMonitorLeaseFinishResult.LEASE_LOST);
        assertThat(monitorControlRepository.findById(1L).orElseThrow().getLeaseOwner())
                .isEqualTo("monitor-b");
        assertThat(leaseService.complete(second))
                .isEqualTo(KakaoUnlinkMonitorLeaseFinishResult.COMPLETED);
    }

    @Test
    void scanLeaseFailureRecordsFailedOutcomeAndReleasesLease() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-failure");

        assertThat(
                        leaseService.fail(
                                lease,
                                com.gather.gather.domain.auth.kakao.monitoring.model
                                        .KakaoUnlinkOperationalFailureType.DATABASE))
                .isEqualTo(KakaoUnlinkMonitorLeaseFinishResult.FAILED);

        var control = monitorControlRepository.findById(1L).orElseThrow();
        assertThat(control.getLastScanFailedAt()).isNotNull();
        assertThat(control.getLastScanFailureType()).isEqualTo("DATABASE");
        assertThat(control.getLeaseOwner()).isNull();
        assertThat(control.getLeaseToken()).isNull();
        assertThat(control.getLeaseExpiresAt()).isNull();
    }

    @Test
    @Timeout(10)
    void leaseAcquireAndObservationInsideOuterTransactionDoNotSelfDeadlock() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        KakaoUnlinkObservationResult result =
                outer.execute(
                        status -> {
                            KakaoUnlinkMonitorLease lease = acquire("monitor-outer-transaction");
                            return reconciliationService.observe(
                                    deadObservation(
                                            lease,
                                            fingerprint(),
                                            KakaoUnlinkAlertSeverity.WARNING,
                                            Set.of()));
                        });

        assertThat(result).isNotNull();
        assertThat(result.snapshot().status()).isEqualTo(KakaoUnlinkIncidentStatus.OPEN);
    }

    @Test
    void concurrentInitialObservationConvergesToOneIncidentAndOneDelivery() throws Exception {
        KakaoUnlinkMonitorLease lease = acquire("monitor-concurrent-initial");
        KakaoUnlinkIncidentObservation observation =
                deadObservation(
                        lease,
                        fingerprint(),
                        KakaoUnlinkAlertSeverity.WARNING,
                        Set.of(KakaoUnlinkAlertChannel.DISCORD));
        List<KakaoUnlinkObservationResult> results =
                runConcurrently(() -> reconciliationService.observe(observation));

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(result -> result.snapshot().id())
                .containsOnly(results.get(0).snapshot().id());
        KakaoUnlinkIncident persisted =
                incidentRepository.findById(results.get(0).snapshot().id()).orElseThrow();
        assertThat(persisted.getOccurrenceNo()).isEqualTo(1);
        assertThat(deliveryRepository.count()).isEqualTo(1);
    }

    @Test
    void existingOpenObservationDoesNotRewriteInitialDeliveryPayload() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-existing-initial");
        KakaoUnlinkIncidentFingerprint fingerprint = fingerprint();
        KakaoUnlinkIncidentSnapshot opened =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint,
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of(KakaoUnlinkAlertChannel.DISCORD)))
                        .snapshot();

        reconciliationService.observe(
                deadObservation(
                        lease,
                        fingerprint,
                        KakaoUnlinkAlertSeverity.CRITICAL,
                        Set.of(KakaoUnlinkAlertChannel.DISCORD)));

        assertThat(
                        deliveryRepository.countByIncidentIdAndEventType(
                                opened.id(), KakaoUnlinkAlertEventType.INITIAL))
                .isEqualTo(1);
        assertThat(
                        deliveryRepository.countByIncidentIdAndEventType(
                                opened.id(), KakaoUnlinkAlertEventType.ESCALATED))
                .isEqualTo(1);
    }

    @Test
    void simultaneousReopenIncrementsOccurrenceExactlyOnce() throws Exception {
        KakaoUnlinkMonitorLease lease = acquire("monitor-reopen");
        KakaoUnlinkIncidentObservation observation =
                deadObservation(lease, fingerprint(), KakaoUnlinkAlertSeverity.WARNING, Set.of());
        KakaoUnlinkIncidentSnapshot opened = reconciliationService.observe(observation).snapshot();
        reconciliationService.resolve(new KakaoUnlinkIncidentResolution(lease, opened.id()));

        List<KakaoUnlinkObservationResult> reopened =
                runConcurrently(() -> reconciliationService.observe(observation));

        assertThat(reopened).extracting(result -> result.snapshot().occurrenceNo()).containsOnly(2);
        assertThat(incidentRepository.findById(opened.id()).orElseThrow().getOccurrenceNo())
                .isEqualTo(2);
    }

    @Test
    void concurrentSameSeverityEscalationOccursOnceAndConvergesToHighestSeverity()
            throws Exception {
        KakaoUnlinkMonitorLease lease = acquire("monitor-escalation");
        KakaoUnlinkIncidentFingerprint fingerprint = fingerprint();
        KakaoUnlinkIncidentSnapshot opened =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint,
                                        KakaoUnlinkAlertSeverity.INFO,
                                        Set.of()))
                        .snapshot();
        KakaoUnlinkIncidentObservation critical =
                deadObservation(lease, fingerprint, KakaoUnlinkAlertSeverity.CRITICAL, Set.of());

        runConcurrently(() -> reconciliationService.observe(critical));

        KakaoUnlinkIncident incident = incidentRepository.findById(opened.id()).orElseThrow();
        assertThat(incident.getSeverity()).isEqualTo(KakaoUnlinkAlertSeverity.CRITICAL);
        assertThat(incident.getSeverityEscalationNo()).isEqualTo(1);
        assertThat(
                        deliveryRepository.countByIncidentIdAndEventType(
                                incident.getId(), KakaoUnlinkAlertEventType.ESCALATED))
                .isEqualTo(1);
    }

    @Test
    void suppressionPreservesObservationAndUsesExplicitCauseOccurrence() {
        KakaoUnlinkMonitorLease firstLease = acquire("monitor-suppression-1");
        KakaoUnlinkIncidentSnapshot cause =
                reconciliationService
                        .observe(
                                deadObservation(
                                        firstLease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.CRITICAL,
                                        Set.of()))
                        .snapshot();
        KakaoUnlinkIncidentSnapshot child =
                reconciliationService.observe(populationObservation(firstLease)).snapshot();
        reconciliationService.suppress(
                new KakaoUnlinkIncidentSuppression(
                        firstLease, child.id(), cause.id(), cause.occurrenceNo()));
        leaseService.complete(firstLease);

        KakaoUnlinkMonitorLease secondLease = acquire("monitor-suppression-2");
        KakaoUnlinkIncidentObservation newer = populationObservation(secondLease);
        reconciliationService.observe(newer);

        KakaoUnlinkIncident persisted = incidentRepository.findById(child.id()).orElseThrow();
        assertThat(persisted.getNotificationState())
                .isEqualTo(KakaoUnlinkNotificationState.SUPPRESSED);
        assertThat(persisted.getSuppressedByIncident().getId()).isEqualTo(cause.id());
        assertThat(persisted.getLastObservedScanSequence()).isEqualTo(secondLease.scanSequence());
    }

    @Test
    @Timeout(10)
    void concurrentSuppressionsCompleteWithoutLockOrderDeadlock() throws Exception {
        KakaoUnlinkMonitorLease lease = acquire("monitor-concurrent-suppression");
        KakaoUnlinkIncidentSnapshot firstCause =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.CRITICAL,
                                        Set.of()))
                        .snapshot();
        KakaoUnlinkIncidentSnapshot firstChild =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of()))
                        .snapshot();
        KakaoUnlinkIncidentSnapshot secondChild =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of()))
                        .snapshot();
        KakaoUnlinkIncidentSnapshot secondCause =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.CRITICAL,
                                        Set.of()))
                        .snapshot();

        List<KakaoUnlinkIncidentSnapshot> suppressed =
                runConcurrently(
                        () ->
                                reconciliationService.suppress(
                                        new KakaoUnlinkIncidentSuppression(
                                                lease,
                                                firstChild.id(),
                                                firstCause.id(),
                                                firstCause.occurrenceNo())),
                        () ->
                                reconciliationService.suppress(
                                        new KakaoUnlinkIncidentSuppression(
                                                lease,
                                                secondChild.id(),
                                                secondCause.id(),
                                                secondCause.occurrenceNo())));

        assertThat(suppressed)
                .extracting(KakaoUnlinkIncidentSnapshot::notificationState)
                .containsOnly(KakaoUnlinkNotificationState.SUPPRESSED);
        assertThat(
                        incidentRepository
                                .findById(firstChild.id())
                                .orElseThrow()
                                .getSuppressedByIncident()
                                .getId())
                .isEqualTo(firstCause.id());
        assertThat(
                        incidentRepository
                                .findById(secondChild.id())
                                .orElseThrow()
                                .getSuppressedByIncident()
                                .getId())
                .isEqualTo(secondCause.id());
    }

    @Test
    void existingOpenIncidentFillsMissingReminderSchedule() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-reminder-fill");
        KakaoUnlinkIncidentFingerprint fingerprint = fingerprint();
        KakaoUnlinkIncidentSnapshot opened =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint,
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of(KakaoUnlinkAlertChannel.DISCORD)))
                        .snapshot();
        LocalDateTime reminderAt = monitorControlRepository.currentUtcDateTime().plusMinutes(1);
        KakaoUnlinkIncidentObservation scheduled =
                new KakaoUnlinkIncidentObservation(
                        lease,
                        fingerprint,
                        KakaoUnlinkAlertType.DEAD_TASK,
                        KakaoUnlinkAlertSeverity.WARNING,
                        new DeadTaskSafeDetails(
                                100,
                                0,
                                1,
                                KakaoUnlinkTaskStatus.DEAD,
                                KakaoUnlinkTaskErrorType.REQUEST,
                                400,
                                -1),
                        reminderAt,
                        null,
                        Set.of(),
                        Set.of());

        reconciliationService.observe(scheduled);

        assertThat(incidentRepository.findOperationalReminderCandidates(reminderAt.plusSeconds(1)))
                .extracting(KakaoUnlinkIncident::getId)
                .contains(opened.id());
    }

    @Test
    void recordReminderPersistsSequencePayloadAndNextSchedule() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-record-reminder");
        KakaoUnlinkIncidentSnapshot opened =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of()))
                        .snapshot();
        LocalDateTime firstSchedule = monitorControlRepository.currentUtcDateTime().plusMinutes(5);
        LocalDateTime secondSchedule = firstSchedule.plusMinutes(5);

        var first =
                reconciliationService.recordReminder(
                        new KakaoUnlinkReminderRequest(
                                lease,
                                opened.id(),
                                KakaoUnlinkAlertChannel.DISCORD,
                                firstSchedule));
        var second =
                reconciliationService.recordReminder(
                        new KakaoUnlinkReminderRequest(
                                lease,
                                opened.id(),
                                KakaoUnlinkAlertChannel.DISCORD,
                                secondSchedule));

        assertThat(first.created()).isTrue();
        assertThat(first.eventSequence()).isEqualTo(1);
        assertThat(second.created()).isTrue();
        assertThat(second.eventSequence()).isEqualTo(2);
        assertThat(
                        incidentRepository
                                .findById(opened.id())
                                .orElseThrow()
                                .getNextDiscordReminderAt())
                .isEqualTo(secondSchedule);
        assertThat(
                        deliveryRepository.countByIncidentIdAndEventType(
                                opened.id(), KakaoUnlinkAlertEventType.REMINDER))
                .isEqualTo(2);
    }

    @Test
    void suppressionRejectsChainsAndReleaseGraceControlsReminderEligibility() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-suppression-lifecycle");
        KakaoUnlinkIncidentSnapshot cause =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.CRITICAL,
                                        Set.of()))
                        .snapshot();
        LocalDateTime reminderAt = monitorControlRepository.currentUtcDateTime().plusMinutes(1);
        KakaoUnlinkIncidentSnapshot child =
                reconciliationService.observe(populationObservation(lease, reminderAt)).snapshot();
        reconciliationService.suppress(
                new KakaoUnlinkIncidentSuppression(
                        lease, child.id(), cause.id(), cause.occurrenceNo()));

        assertThatThrownBy(
                        () ->
                                reconciliationService.suppress(
                                        new KakaoUnlinkIncidentSuppression(
                                                lease,
                                                cause.id(),
                                                child.id(),
                                                child.occurrenceNo())))
                .isInstanceOf(IllegalStateException.class);

        reconciliationService.resolve(new KakaoUnlinkIncidentResolution(lease, cause.id()));
        assertThat(incidentRepository.findStaleSuppressionCandidates())
                .extracting(KakaoUnlinkIncident::getId)
                .contains(child.id());

        LocalDateTime eligibleAt = reminderAt.plusMinutes(5);
        reconciliationService.releaseSuppression(
                new KakaoUnlinkSuppressionRelease(lease, child.id(), eligibleAt));
        assertThat(incidentRepository.findOperationalReminderCandidates(reminderAt.plusMinutes(2)))
                .extracting(KakaoUnlinkIncident::getId)
                .doesNotContain(child.id());
        assertThat(incidentRepository.findOperationalReminderCandidates(eligibleAt.plusSeconds(1)))
                .extracting(KakaoUnlinkIncident::getId)
                .contains(child.id());
    }

    @Test
    void suppressedIncidentDoesNotEnqueueEscalatedDelivery() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-suppressed-escalation");
        KakaoUnlinkIncidentSnapshot cause =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.CRITICAL,
                                        Set.of()))
                        .snapshot();
        KakaoUnlinkIncidentSnapshot child =
                reconciliationService.observe(populationObservation(lease)).snapshot();
        reconciliationService.suppress(
                new KakaoUnlinkIncidentSuppression(
                        lease, child.id(), cause.id(), cause.occurrenceNo()));

        reconciliationService.observe(
                populationObservation(
                        lease,
                        KakaoUnlinkAlertSeverity.CRITICAL,
                        null,
                        Set.of(KakaoUnlinkAlertChannel.DISCORD)));

        assertThat(
                        deliveryRepository.countByIncidentIdAndEventType(
                                child.id(), KakaoUnlinkAlertEventType.ESCALATED))
                .isZero();
    }

    @Test
    void recoveredDeliveryUsesLeaseAndReturnsNormalNonApplicableOutcomes() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-recovered");
        KakaoUnlinkIncidentSnapshot incident =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of(KakaoUnlinkAlertChannel.DISCORD)))
                        .snapshot();
        jdbcTemplate.update(
                "update kakao_unlink_alert_delivery set status = 'SUCCEEDED', sent_at = UTC_TIMESTAMP(6) where incident_id = ? and channel = 'DISCORD'",
                incident.id());
        reconciliationService.resolve(new KakaoUnlinkIncidentResolution(lease, incident.id()));

        KakaoUnlinkRecoveredDeliveryResult noEmailHistory =
                reconciliationService.enqueueRecovered(
                        new KakaoUnlinkRecoveredDeliveryRequest(
                                lease,
                                incident.id(),
                                incident.occurrenceNo(),
                                KakaoUnlinkAlertChannel.EMAIL));
        KakaoUnlinkRecoveredDeliveryResult enqueued =
                reconciliationService.enqueueRecovered(
                        new KakaoUnlinkRecoveredDeliveryRequest(
                                lease,
                                incident.id(),
                                incident.occurrenceNo(),
                                KakaoUnlinkAlertChannel.DISCORD));
        KakaoUnlinkRecoveredDeliveryResult wrongOccurrence =
                reconciliationService.enqueueRecovered(
                        new KakaoUnlinkRecoveredDeliveryRequest(
                                lease,
                                incident.id(),
                                incident.occurrenceNo() + 1,
                                KakaoUnlinkAlertChannel.DISCORD));

        assertThat(noEmailHistory.outcome())
                .isEqualTo(KakaoUnlinkRecoveredDeliveryResult.Outcome.NO_SUCCESSFUL_PROBLEM_ALERT);
        assertThat(enqueued.outcome())
                .isEqualTo(KakaoUnlinkRecoveredDeliveryResult.Outcome.ENQUEUED);
        assertThat(wrongOccurrence.outcome())
                .isEqualTo(KakaoUnlinkRecoveredDeliveryResult.Outcome.NOT_APPLICABLE);

        leaseService.complete(lease);
        assertThatThrownBy(
                        () ->
                                reconciliationService.enqueueRecovered(
                                        new KakaoUnlinkRecoveredDeliveryRequest(
                                                lease,
                                                incident.id(),
                                                incident.occurrenceNo(),
                                                KakaoUnlinkAlertChannel.DISCORD)))
                .isInstanceOf(KakaoUnlinkMonitorLeaseLostException.class);
    }

    @Test
    void mysqlEnforcesCriticalCheckConstraints() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "update kakao_unlink_incident set resolved_at = UTC_TIMESTAMP(6) where alert_type = 'SYNTHETIC_TEST'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "update kakao_unlink_incident set notification_state = 'SUPPRESSED' where alert_type = 'SYNTHETIC_TEST'"))
                .isInstanceOf(DataAccessException.class);

        KakaoUnlinkMonitorLease lease = acquire("monitor-check-constraints");
        KakaoUnlinkIncidentSnapshot incident =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of(KakaoUnlinkAlertChannel.DISCORD)))
                        .snapshot();
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "update kakao_unlink_alert_delivery set event_sequence = 2 where incident_id = ? and event_type = 'INITIAL'",
                                        incident.id()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "update kakao_unlink_alert_delivery set status = 'SUCCEEDED' where incident_id = ?",
                                        incident.id()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void syntheticSingletonIsExcludedAndAllocatesOneSequencePerExecution() {
        KakaoUnlinkIncident synthetic =
                incidentRepository
                        .findByFingerprint(KakaoUnlinkIncidentFingerprint.SYNTHETIC_TEST_VALUE)
                        .orElseThrow();

        assertThat(incidentRepository.countOperationalOpen()).isZero();
        assertThat(incidentRepository.findOperationalOpen()).doesNotContain(synthetic);
        assertThat(
                        incidentRepository.findOperationalReminderCandidates(
                                LocalDateTime.now().plusYears(1)))
                .doesNotContain(synthetic);

        var first =
                reconciliationService.enqueueSyntheticTest(
                        Set.of(KakaoUnlinkAlertChannel.DISCORD, KakaoUnlinkAlertChannel.EMAIL));
        var second =
                reconciliationService.enqueueSyntheticTest(Set.of(KakaoUnlinkAlertChannel.DISCORD));

        assertThat(first).extracting(result -> result.eventSequence()).containsOnly(1);
        assertThat(second).extracting(result -> result.eventSequence()).containsOnly(2);
        assertThatThrownBy(
                        () ->
                                reconciliationService.resolve(
                                        new KakaoUnlinkIncidentResolution(
                                                acquire("monitor-synthetic-resolve"),
                                                synthetic.getId())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void successfulProblemHistoryIsStrictByOccurrenceAndChannel() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-history");
        KakaoUnlinkIncidentSnapshot incident =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of(KakaoUnlinkAlertChannel.DISCORD)))
                        .snapshot();
        jdbcTemplate.update(
                "update kakao_unlink_alert_delivery set status = 'SUCCEEDED', sent_at = UTC_TIMESTAMP(6) where incident_id = ?",
                incident.id());

        assertThat(
                        reconciliationService.hasSuccessfulProblemDelivery(
                                incident.id(), 1, KakaoUnlinkAlertChannel.DISCORD))
                .isTrue();
        assertThat(
                        reconciliationService.hasSuccessfulProblemDelivery(
                                incident.id(), 1, KakaoUnlinkAlertChannel.EMAIL))
                .isFalse();
        assertThat(
                        reconciliationService.hasSuccessfulProblemDelivery(
                                incident.id(), 2, KakaoUnlinkAlertChannel.DISCORD))
                .isFalse();
    }

    @Test
    void fingerprintInvariantFailureDoesNotRollbackAnotherFingerprint() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-isolation");
        KakaoUnlinkIncidentFingerprint conflictingFingerprint = fingerprint();
        KakaoUnlinkIncidentSnapshot conflicting =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        conflictingFingerprint,
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of()))
                        .snapshot();
        jdbcTemplate.update(
                "update kakao_unlink_incident set alert_type = 'BACKLOG_ACCUMULATION' where id = ?",
                conflicting.id());

        assertThatThrownBy(
                        () ->
                                reconciliationService.observe(
                                        deadObservation(
                                                lease,
                                                conflictingFingerprint,
                                                KakaoUnlinkAlertSeverity.WARNING,
                                                Set.of())))
                .isInstanceOf(KakaoUnlinkMonitoringInvariantException.class);

        KakaoUnlinkIncidentSnapshot independent =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of()))
                        .snapshot();
        assertThat(incidentRepository.findById(independent.id())).isPresent();
    }

    @Test
    void idempotentDeliveryIgnoresGeneratedAtButRejectsDifferentLogicalPayload() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-payload-invariant");
        KakaoUnlinkIncidentSnapshot snapshot =
                reconciliationService
                        .observe(
                                deadObservation(
                                        lease,
                                        fingerprint(),
                                        KakaoUnlinkAlertSeverity.WARNING,
                                        Set.of()))
                        .snapshot();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(
                status -> {
                    KakaoUnlinkIncident incident =
                            incidentRepository.findByIdForUpdate(snapshot.id()).orElseThrow();
                    LocalDateTime now = deliveryRepository.currentUtcDateTime();
                    deliveryPersistenceService.enqueueExact(
                            incident,
                            KakaoUnlinkAlertEventType.INITIAL,
                            1,
                            KakaoUnlinkAlertChannel.DISCORD,
                            payload(incident, Instant.parse("2026-08-08T00:00:00Z")),
                            now);
                });

        transaction.executeWithoutResult(
                status -> {
                    KakaoUnlinkIncident incident =
                            incidentRepository.findByIdForUpdate(snapshot.id()).orElseThrow();
                    LocalDateTime now = deliveryRepository.currentUtcDateTime();
                    assertThat(
                                    deliveryPersistenceService
                                            .enqueueExact(
                                                    incident,
                                                    KakaoUnlinkAlertEventType.INITIAL,
                                                    1,
                                                    KakaoUnlinkAlertChannel.DISCORD,
                                                    payload(
                                                            incident,
                                                            Instant.parse("2026-08-08T00:00:01Z")),
                                                    now)
                                            .created())
                            .isFalse();
                });

        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            KakaoUnlinkIncident incident =
                                                    incidentRepository
                                                            .findByIdForUpdate(snapshot.id())
                                                            .orElseThrow();
                                            LocalDateTime now =
                                                    deliveryRepository.currentUtcDateTime();
                                            deliveryPersistenceService.enqueueIfAbsent(
                                                    incident,
                                                    KakaoUnlinkAlertEventType.INITIAL,
                                                    1,
                                                    KakaoUnlinkAlertChannel.DISCORD,
                                                    payloadWithDifferentDetails(incident),
                                                    now);
                                        }))
                .isInstanceOf(KakaoUnlinkMonitoringInvariantException.class);
    }

    @Test
    void databaseUtcNowMatchesUtcBoundary() {
        LocalDateTime databaseNow = monitorControlRepository.currentUtcDateTime();
        LocalDateTime jvmUtcNow = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        assertThat(Duration.between(databaseNow, jvmUtcNow).abs())
                .isLessThan(Duration.ofSeconds(5));
    }

    private KakaoUnlinkMonitorLease acquire(String owner) {
        KakaoUnlinkMonitorLeaseAcquireResult result =
                leaseService.tryAcquire(owner, Duration.ofMinutes(3));
        assertThat(result.outcome())
                .isEqualTo(KakaoUnlinkMonitorLeaseAcquireResult.Outcome.ACQUIRED);
        return result.lease();
    }

    private KakaoUnlinkIncidentObservation deadObservation(
            KakaoUnlinkMonitorLease lease,
            KakaoUnlinkIncidentFingerprint fingerprint,
            KakaoUnlinkAlertSeverity severity,
            Set<KakaoUnlinkAlertChannel> initialChannels) {
        return new KakaoUnlinkIncidentObservation(
                lease,
                fingerprint,
                KakaoUnlinkAlertType.DEAD_TASK,
                severity,
                new DeadTaskSafeDetails(
                        100,
                        0,
                        1,
                        KakaoUnlinkTaskStatus.DEAD,
                        KakaoUnlinkTaskErrorType.REQUEST,
                        400,
                        -1),
                null,
                null,
                initialChannels,
                Set.of(KakaoUnlinkAlertChannel.DISCORD));
    }

    private KakaoUnlinkIncidentObservation populationObservation(KakaoUnlinkMonitorLease lease) {
        return populationObservation(lease, KakaoUnlinkAlertSeverity.WARNING, null, Set.of());
    }

    private KakaoUnlinkIncidentObservation populationObservation(
            KakaoUnlinkMonitorLease lease, LocalDateTime nextDiscordReminderAt) {
        return populationObservation(
                lease, KakaoUnlinkAlertSeverity.WARNING, nextDiscordReminderAt, Set.of());
    }

    private KakaoUnlinkIncidentObservation populationObservation(
            KakaoUnlinkMonitorLease lease,
            KakaoUnlinkAlertSeverity severity,
            LocalDateTime nextDiscordReminderAt,
            Set<KakaoUnlinkAlertChannel> escalationChannels) {
        return new KakaoUnlinkIncidentObservation(
                lease,
                KakaoUnlinkIncidentFingerprint.singleton(KakaoUnlinkAlertType.BACKLOG_ACCUMULATION),
                KakaoUnlinkAlertType.BACKLOG_ACCUMULATION,
                severity,
                new TaskPopulationSafeDetails(KakaoUnlinkTaskStatus.PENDING, 10, 600, 300),
                nextDiscordReminderAt,
                null,
                Set.of(),
                escalationChannels);
    }

    private OperationalAlertPayloadSnapshot payload(
            KakaoUnlinkIncident incident, Instant generatedAt) {
        return new OperationalAlertPayloadSnapshot(
                OperationalAlertPayloadSnapshot.CURRENT_SCHEMA_VERSION,
                incident.getFingerprint(),
                incident.getAlertType(),
                incident.getOccurrenceNo(),
                incident.getSeverity(),
                KakaoUnlinkAlertEventType.INITIAL,
                1,
                KakaoUnlinkAlertChannel.DISCORD,
                generatedAt,
                incident.getSafeDetails());
    }

    private OperationalAlertPayloadSnapshot payloadWithDifferentDetails(
            KakaoUnlinkIncident incident) {
        return new OperationalAlertPayloadSnapshot(
                OperationalAlertPayloadSnapshot.CURRENT_SCHEMA_VERSION,
                incident.getFingerprint(),
                incident.getAlertType(),
                incident.getOccurrenceNo(),
                incident.getSeverity(),
                KakaoUnlinkAlertEventType.INITIAL,
                1,
                KakaoUnlinkAlertChannel.DISCORD,
                Instant.parse("2026-08-08T00:00:02Z"),
                new DeadTaskSafeDetails(
                        100,
                        0,
                        2,
                        KakaoUnlinkTaskStatus.DEAD,
                        KakaoUnlinkTaskErrorType.REQUEST,
                        400,
                        -1));
    }

    private KakaoUnlinkIncidentFingerprint fingerprint() {
        return KakaoUnlinkIncidentFingerprint.deadTask(FINGERPRINT_SEQUENCE.incrementAndGet(), 0);
    }

    private <T> List<T> runConcurrently(ThrowingSupplier<T> supplier) throws Exception {
        return runConcurrently(supplier, supplier);
    }

    private <T> List<T> runConcurrently(
            ThrowingSupplier<T> firstSupplier, ThrowingSupplier<T> secondSupplier)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> first =
                    executor.submit(() -> executeAfterSignal(firstSupplier, ready, start));
            Future<T> second =
                    executor.submit(() -> executeAfterSignal(secondSupplier, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private <T> T executeAfterSignal(
            ThrowingSupplier<T> supplier, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return supplier.get();
    }

    private void cleanDatabase() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            jdbcTemplate.update("delete from kakao_unlink_alert_delivery");
                            jdbcTemplate.update(
                                    "update kakao_unlink_incident set notification_state = 'ELIGIBLE', suppressed_by_incident_id = null, suppressed_by_occurrence_no = null, suppressed_at = null where alert_type <> 'SYNTHETIC_TEST'");
                            jdbcTemplate.update(
                                    "delete from kakao_unlink_incident where alert_type <> 'SYNTHETIC_TEST'");
                            jdbcTemplate.update(
                                    "update kakao_unlink_monitor_control set scan_sequence = 0, lease_token = null, lease_owner = null, lease_acquired_at = null, lease_expires_at = null, last_scan_started_at = null, last_scan_completed_at = null, last_scan_failed_at = null, last_scan_failure_type = null, updated_at = UTC_TIMESTAMP(6), version = 0 where id = 1");
                            jdbcTemplate.update(
                                    "update kakao_unlink_worker_control set last_poll_started_at = null, last_poll_completed_at = null, last_poll_failed_at = null, last_poll_failure_type = null where id = 1");
                        });
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
