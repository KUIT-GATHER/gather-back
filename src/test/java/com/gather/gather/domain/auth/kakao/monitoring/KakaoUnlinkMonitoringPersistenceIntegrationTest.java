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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
                        "select count(*) from flyway_schema_history where version = '52' and success = 1",
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
    void concurrentInitialObservationConvergesToOneIncidentAndOneDelivery() throws Exception {
        KakaoUnlinkMonitorLease lease = acquire("monitor-concurrent-initial");
        KakaoUnlinkIncidentObservation observation =
                deadObservation(
                        lease,
                        fingerprint(),
                        KakaoUnlinkAlertSeverity.WARNING,
                        Set.of(KakaoUnlinkAlertChannel.DISCORD));
        List<KakaoUnlinkIncidentSnapshot> results =
                runConcurrently(() -> reconciliationService.observe(observation));

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(KakaoUnlinkIncidentSnapshot::id)
                .containsOnly(results.get(0).id());
        KakaoUnlinkIncident persisted =
                incidentRepository.findById(results.get(0).id()).orElseThrow();
        assertThat(persisted.getOccurrenceNo()).isEqualTo(1);
        assertThat(deliveryRepository.count()).isEqualTo(1);
    }

    @Test
    void simultaneousReopenIncrementsOccurrenceExactlyOnce() throws Exception {
        KakaoUnlinkMonitorLease lease = acquire("monitor-reopen");
        KakaoUnlinkIncidentObservation observation =
                deadObservation(lease, fingerprint(), KakaoUnlinkAlertSeverity.WARNING, Set.of());
        KakaoUnlinkIncidentSnapshot opened = reconciliationService.observe(observation);
        reconciliationService.resolve(new KakaoUnlinkIncidentResolution(lease, opened.id()));

        List<KakaoUnlinkIncidentSnapshot> reopened =
                runConcurrently(() -> reconciliationService.observe(observation));

        assertThat(reopened).extracting(KakaoUnlinkIncidentSnapshot::occurrenceNo).containsOnly(2);
        assertThat(incidentRepository.findById(opened.id()).orElseThrow().getOccurrenceNo())
                .isEqualTo(2);
    }

    @Test
    void concurrentSameSeverityEscalationOccursOnceAndConvergesToHighestSeverity()
            throws Exception {
        KakaoUnlinkMonitorLease lease = acquire("monitor-escalation");
        String fingerprint = fingerprint();
        KakaoUnlinkIncidentSnapshot opened =
                reconciliationService.observe(
                        deadObservation(
                                lease, fingerprint, KakaoUnlinkAlertSeverity.INFO, Set.of()));
        KakaoUnlinkIncidentObservation critical =
                deadObservation(lease, fingerprint, KakaoUnlinkAlertSeverity.CRITICAL, Set.of());

        runConcurrently(() -> reconciliationService.observe(critical));

        KakaoUnlinkIncident incident = incidentRepository.findById(opened.id()).orElseThrow();
        assertThat(incident.getSeverity()).isEqualTo(KakaoUnlinkAlertSeverity.CRITICAL);
        assertThat(incident.getSeverityEscalationNo()).isEqualTo(1);
    }

    @Test
    void suppressionPreservesObservationAndUsesExplicitCauseOccurrence() {
        KakaoUnlinkMonitorLease firstLease = acquire("monitor-suppression-1");
        KakaoUnlinkIncidentSnapshot cause =
                reconciliationService.observe(
                        deadObservation(
                                firstLease,
                                fingerprint(),
                                KakaoUnlinkAlertSeverity.CRITICAL,
                                Set.of()));
        KakaoUnlinkIncidentSnapshot child =
                reconciliationService.observe(populationObservation(firstLease, fingerprint()));
        reconciliationService.suppress(
                new KakaoUnlinkIncidentSuppression(
                        firstLease, child.id(), cause.id(), cause.occurrenceNo()));
        leaseService.complete(firstLease);

        KakaoUnlinkMonitorLease secondLease = acquire("monitor-suppression-2");
        KakaoUnlinkIncidentObservation newer =
                populationObservation(secondLease, child.fingerprint());
        reconciliationService.observe(newer);

        KakaoUnlinkIncident persisted = incidentRepository.findById(child.id()).orElseThrow();
        assertThat(persisted.getNotificationState())
                .isEqualTo(KakaoUnlinkNotificationState.SUPPRESSED);
        assertThat(persisted.getSuppressedByIncident().getId()).isEqualTo(cause.id());
        assertThat(persisted.getLastObservedScanSequence()).isEqualTo(secondLease.scanSequence());
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
                reconciliationService.observe(
                        deadObservation(
                                lease,
                                fingerprint(),
                                KakaoUnlinkAlertSeverity.WARNING,
                                Set.of(KakaoUnlinkAlertChannel.DISCORD)));
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
        String conflictingFingerprint = fingerprint();
        reconciliationService.observe(
                deadObservation(
                        lease, conflictingFingerprint, KakaoUnlinkAlertSeverity.WARNING, Set.of()));

        assertThatThrownBy(
                        () ->
                                reconciliationService.observe(
                                        populationObservation(lease, conflictingFingerprint)))
                .isInstanceOf(IllegalStateException.class);

        KakaoUnlinkIncidentSnapshot independent =
                reconciliationService.observe(
                        deadObservation(
                                lease, fingerprint(), KakaoUnlinkAlertSeverity.WARNING, Set.of()));
        assertThat(incidentRepository.findById(independent.id())).isPresent();
    }

    @Test
    void exactDeliveryRejectsSameNaturalKeyWithDifferentSnapshot() {
        KakaoUnlinkMonitorLease lease = acquire("monitor-payload-invariant");
        KakaoUnlinkIncidentSnapshot snapshot =
                reconciliationService.observe(
                        deadObservation(
                                lease, fingerprint(), KakaoUnlinkAlertSeverity.WARNING, Set.of()));
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
                                            deliveryPersistenceService.enqueueExact(
                                                    incident,
                                                    KakaoUnlinkAlertEventType.INITIAL,
                                                    1,
                                                    KakaoUnlinkAlertChannel.DISCORD,
                                                    payload(
                                                            incident,
                                                            Instant.parse("2026-08-08T00:00:01Z")),
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
            String fingerprint,
            KakaoUnlinkAlertSeverity severity,
            Set<KakaoUnlinkAlertChannel> initialChannels) {
        return new KakaoUnlinkIncidentObservation(
                lease,
                new KakaoUnlinkIncidentFingerprint(fingerprint),
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

    private KakaoUnlinkIncidentObservation populationObservation(
            KakaoUnlinkMonitorLease lease, String fingerprint) {
        return new KakaoUnlinkIncidentObservation(
                lease,
                new KakaoUnlinkIncidentFingerprint(fingerprint),
                KakaoUnlinkAlertType.BACKLOG_ACCUMULATION,
                KakaoUnlinkAlertSeverity.WARNING,
                new TaskPopulationSafeDetails(KakaoUnlinkTaskStatus.PENDING, 10, 600, 300),
                null,
                null,
                Set.of(),
                Set.of());
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

    private String fingerprint() {
        return "KAKAO_UNLINK:DEAD_TASK:" + FINGERPRINT_SEQUENCE.incrementAndGet() + ":0";
    }

    private <T> List<T> runConcurrently(ThrowingSupplier<T> supplier) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> first = executor.submit(() -> executeAfterSignal(supplier, ready, start));
            Future<T> second = executor.submit(() -> executeAfterSignal(supplier, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
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
