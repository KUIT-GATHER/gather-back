package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskErrorType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus;
import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControl;
import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControlStatus;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminApiClient;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkDisposition;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkResult;
import com.gather.gather.domain.auth.repository.KakaoUnlinkTaskRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkWorkerControlRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.user.repository.ProfileImageUploadRepository;
import com.gather.gather.domain.user.service.ProfileImageDeletionService;
import com.gather.gather.global.infra.s3.ObjectStorage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class KakaoUnlinkWorkerIntegrationTest {

    private static final LocalDateTime TASK_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final AtomicLong PROVIDER_ID_SEQUENCE = new AtomicLong(123_456_789L);

    @Autowired private UserRepository userRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private KakaoUnlinkTaskRepository taskRepository;
    @Autowired private KakaoUnlinkWorkerControlRepository controlRepository;
    @Autowired private ProfileImageUploadRepository profileImageUploadRepository;
    @Autowired private SocialAccountProviderIdCipher providerIdCipher;
    @Autowired private KakaoUnlinkClaimService claimService;
    @Autowired private KakaoUnlinkTransactionService transactionService;
    @Autowired private KakaoUnlinkResultService resultService;
    @Autowired private KakaoUnlinkWorkerResumeService resumeService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ObjectStorage objectStorage;
    @MockitoBean private KakaoAdminApiClient adminApiClient;
    @MockitoSpyBean private ProfileImageDeletionService profileImageDeletionService;

    private Long userId;
    private Long socialAccountId;
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdSocialAccountIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            jdbcTemplate.update(
                                    "UPDATE kakao_unlink_worker_control SET status = 'ACTIVE', blocked_at = NULL, blocked_reason = NULL, last_http_status = NULL, last_kakao_code = NULL WHERE id = ?",
                                    KakaoUnlinkWorkerControl.SINGLETON_ID);
                            for (Long createdUserId : createdUserIds) {
                                jdbcTemplate.update(
                                        "DELETE FROM profile_image_upload WHERE user_id = ?",
                                        createdUserId);
                            }
                            for (Long createdSocialAccountId : createdSocialAccountIds) {
                                jdbcTemplate.update(
                                        "DELETE FROM kakao_unlink_task WHERE social_account_id = ?",
                                        createdSocialAccountId);
                                jdbcTemplate.update(
                                        "DELETE FROM social_account WHERE id = ?",
                                        createdSocialAccountId);
                            }
                            for (Long createdUserId : createdUserIds) {
                                jdbcTemplate.update(
                                        "DELETE FROM users WHERE id = ?", createdUserId);
                            }
                        });
        createdUserIds.clear();
        createdSocialAccountIds.clear();
    }

    @Test
    void concurrentClaim_allowsOnlyOneOwnerForSameTask() {
        Fixture fixture = createFixture(false, false);

        CompletableFuture<List<KakaoUnlinkClaim>> first =
                CompletableFuture.supplyAsync(claimService::claimBatch);
        CompletableFuture<List<KakaoUnlinkClaim>> second =
                CompletableFuture.supplyAsync(claimService::claimBatch);
        List<KakaoUnlinkClaim> combined =
                CompletableFuture.allOf(first, second)
                        .thenApply(ignored -> List.of(first.join(), second.join()))
                        .join()
                        .stream()
                        .flatMap(List::stream)
                        .filter(claim -> claim.taskId().equals(fixture.taskId()))
                        .toList();

        assertThat(combined).hasSize(1);
        assertThat(taskRepository.findById(fixture.taskId()).orElseThrow().getStatus())
                .isEqualTo(KakaoUnlinkTaskStatus.PROCESSING);
    }

    @Test
    void claimsUnlockedTaskWhileEarlierDueTaskIsLocked() throws Exception {
        Fixture earlier = createFixture(false, false);
        Fixture later = createFixture(false, false);
        CountDownLatch taskLocked = new CountDownLatch(1);
        CountDownLatch releaseTaskLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> locker =
                executor.submit(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    taskRepository
                                                            .findByIdForUpdate(earlier.taskId())
                                                            .orElseThrow();
                                                    taskLocked.countDown();
                                                    await(releaseTaskLock);
                                                }));

        try {
            assertThat(taskLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<List<KakaoUnlinkClaim>> claimFuture = executor.submit(claimService::claimBatch);
            List<KakaoUnlinkClaim> claims = claimFuture.get(5, TimeUnit.SECONDS);

            assertThat(locker).isNotDone();
            assertThat(claims)
                    .extracting(KakaoUnlinkClaim::taskId)
                    .contains(later.taskId())
                    .doesNotContain(earlier.taskId());

            KakaoUnlinkTask lockedTask = taskRepository.findById(earlier.taskId()).orElseThrow();
            assertThat(lockedTask.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PENDING);
            assertThat(lockedTask.getClaimToken()).isNull();
            assertThat(lockedTask.getLeaseExpiresAt()).isNull();

            KakaoUnlinkTask claimedTask = taskRepository.findById(later.taskId()).orElseThrow();
            assertThat(claimedTask.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PROCESSING);
            assertThat(claimedTask.getClaimToken()).isNotBlank();
            assertThat(claimedTask.getLeaseExpiresAt()).isNotNull();
        } finally {
            releaseTaskLock.countDown();
            try {
                locker.get(5, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void expiredLeaseReclaim_fencesOldWorkerResult() {
        Fixture fixture = createFixture(false, false);
        KakaoUnlinkClaim oldClaim = claimFor(fixture.taskId());
        KakaoUnlinkAttempt oldAttempt = transactionService.reserveAttempt(oldClaim).attempt();
        assertThat(taskRepository.findById(fixture.taskId()).orElseThrow().getAttemptCount())
                .isOne();
        jdbcTemplate.update(
                "UPDATE kakao_unlink_task SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                fixture.taskId());

        KakaoUnlinkClaim newClaim = claimFor(fixture.taskId());
        resultService.apply(
                oldAttempt,
                new KakaoAdminUnlinkResult(KakaoAdminUnlinkDisposition.SUCCESS, 200, null, null));

        KakaoUnlinkTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(newClaim.claimToken()).isNotEqualTo(oldClaim.claimToken());
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PROCESSING);
        assertThat(task.getClaimToken()).isEqualTo(newClaim.claimToken());
        assertThat(
                        socialAccountRepository
                                .findById(socialAccountId)
                                .orElseThrow()
                                .isUnlinkPending())
                .isTrue();
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWAL_PENDING);
    }

    @Test
    void successFinalizer_commitsWithdrawalIdentifierPurgeAndDurableImageDeletion() {
        Fixture fixture = createFixture(false, true);
        KakaoUnlinkClaim claim = claimFor(fixture.taskId());
        KakaoUnlinkAttempt attempt = transactionService.reserveAttempt(claim).attempt();

        resultService.apply(
                attempt,
                new KakaoAdminUnlinkResult(KakaoAdminUnlinkDisposition.SUCCESS, 200, null, null));

        KakaoUnlinkTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        SocialAccount account = socialAccountRepository.findById(socialAccountId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.SUCCEEDED);
        assertThat(task.getAttemptCount()).isOne();
        assertThat(task.getLastAttemptAt()).isNotNull();
        assertThat(account.isUnlinked()).isTrue();
        assertThat(account.getUnlinkedAt()).isNotNull();
        assertThat(account.getProviderUserKey()).isEqualTo(fixture.providerUserKey());
        assertThat(account.getProviderUserKeyVersion()).isEqualTo(1);
        assertThat(account.getProviderUserIdCiphertext()).isNull();
        assertThat(account.getEncryptionKeyVersion()).isNull();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT provider_user_id FROM social_account WHERE id = ?",
                                String.class,
                                socialAccountId))
                .isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.isAnonymized()).isTrue();
        assertThat(profileImageUploadRepository.findAll())
                .anyMatch(
                        upload ->
                                upload.getUserId().equals(userId)
                                        && fixture.profileImageKey()
                                                .equals(upload.getPreviousObjectKey()));
        verify(objectStorage).delete(fixture.profileImageKey());
    }

    @Test
    void successFinalizer_whenDurableDeletionRegistrationFails_rollsBackEveryChange() {
        Fixture fixture = createFixture(false, true);
        KakaoUnlinkClaim claim = claimFor(fixture.taskId());
        KakaoUnlinkAttempt attempt = transactionService.reserveAttempt(claim).attempt();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                doThrow(
                                                new IllegalStateException(
                                                        "simulated durable deletion failure"))
                                        .when(profileImageDeletionService)
                                        .scheduleDeletion(
                                                eq(userId),
                                                eq(fixture.profileImageKey()),
                                                any(LocalDateTime.class)));

        assertThatThrownBy(
                        () ->
                                resultService.apply(
                                        attempt,
                                        new KakaoAdminUnlinkResult(
                                                KakaoAdminUnlinkDisposition.SUCCESS,
                                                200,
                                                null,
                                                null)))
                .isInstanceOf(IllegalStateException.class);

        KakaoUnlinkTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        SocialAccount account = socialAccountRepository.findById(socialAccountId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PROCESSING);
        assertThat(task.getClaimToken()).isEqualTo(claim.claimToken());
        assertThat(account.isUnlinkPending()).isTrue();
        assertThat(account.getProviderUserIdCiphertext()).isNotNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(user.isAnonymized()).isFalse();
        assertThat(profileImageUploadRepository.findAll())
                .noneMatch(upload -> upload.getUserId().equals(userId));
    }

    @Test
    void sameGenerationAlreadyUnlinked_finalizesWithoutReservation() {
        Fixture fixture = createFixture(true, false);
        LocalDateTime originalLastAttemptAt = TASK_TIME.minusHours(1);
        jdbcTemplate.update(
                "UPDATE kakao_unlink_task SET attempt_count = 3, last_attempt_at = ? WHERE id = ?",
                originalLastAttemptAt,
                fixture.taskId());
        LocalDateTime persistedLastAttemptAt =
                taskRepository.findById(fixture.taskId()).orElseThrow().getLastAttemptAt();
        KakaoUnlinkClaim claim = claimFor(fixture.taskId());
        LocalDateTime originalUnlinkedAt =
                socialAccountRepository.findById(socialAccountId).orElseThrow().getUnlinkedAt();

        assertThat(transactionService.preflight(claim))
                .isEqualTo(KakaoUnlinkPreflightOutcome.LOCAL_FINALIZE);
        resultService.finalizeLocally(claim);

        KakaoUnlinkTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        SocialAccount account = socialAccountRepository.findById(socialAccountId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.SUCCEEDED);
        assertThat(task.getAttemptCount()).isEqualTo(3);
        assertThat(task.getLastAttemptAt()).isEqualTo(persistedLastAttemptAt);
        assertThat(account.getUnlinkedAt()).isEqualTo(originalUnlinkedAt);
        assertThat(account.getProviderUserIdCiphertext()).isNull();
        assertThat(account.getEncryptionKeyVersion()).isNull();
        assertThat(account.getProviderUserKey()).isEqualTo(fixture.providerUserKey());
        assertThat(account.getProviderUserKeyVersion()).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT provider_user_id FROM social_account WHERE id = ?",
                                String.class,
                                socialAccountId))
                .isNull();
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    void configurationFailure_blocksClaimsAndExplicitResumeStartsNewCycle() {
        Fixture fixture = createFixture(false, false);
        KakaoUnlinkClaim claim = claimFor(fixture.taskId());
        KakaoUnlinkAttempt attempt = transactionService.reserveAttempt(claim).attempt();

        KakaoUnlinkBatchAction action =
                resultService.applyConfigurationFailure(
                        attempt,
                        new KakaoAdminUnlinkResult(
                                KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION,
                                401,
                                -401,
                                null));

        assertThat(action).isEqualTo(KakaoUnlinkBatchAction.STOP_BATCH);
        KakaoUnlinkTask blockedTask = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(blockedTask.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.DEAD);
        assertThat(blockedTask.getLastErrorType())
                .isEqualTo(KakaoUnlinkTaskErrorType.CONFIGURATION);
        assertThat(
                        controlRepository
                                .findById(KakaoUnlinkWorkerControl.SINGLETON_ID)
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(KakaoUnlinkWorkerControlStatus.CONFIGURATION_BLOCKED);
        assertThat(claimService.claimBatch()).isEmpty();

        assertThat(
                        resumeService.resumeConfigurationTasks(
                                List.of(fixture.taskId()),
                                "integration-test",
                                KakaoUnlinkResumeReason.CONFIGURATION_VERIFIED))
                .isOne();
        KakaoUnlinkTask resumedTask = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(resumedTask.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PENDING);
        assertThat(resumedTask.getRetryCycle()).isOne();
        assertThat(resumedTask.getAttemptCount()).isZero();
        assertThat(
                        controlRepository
                                .findById(KakaoUnlinkWorkerControl.SINGLETON_ID)
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(KakaoUnlinkWorkerControlStatus.ACTIVE);
    }

    @Test
    void reservation_withMissingProviderIdentifier_becomesInvariantDeadWithoutAttempt() {
        Fixture fixture = createFixture(false, false);
        jdbcTemplate.update(
                "UPDATE social_account SET provider_user_id_ciphertext = NULL, encryption_key_version = NULL WHERE id = ?",
                socialAccountId);
        KakaoUnlinkClaim claim = claimFor(fixture.taskId());

        KakaoUnlinkReservation reservation = transactionService.reserveAttempt(claim);

        KakaoUnlinkTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(reservation.outcome()).isEqualTo(KakaoUnlinkReservation.Outcome.TERMINAL);
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.DEAD);
        assertThat(task.getLastErrorType()).isEqualTo(KakaoUnlinkTaskErrorType.INVARIANT);
        assertThat(task.getAttemptCount()).isZero();
    }

    @Test
    void reservation_withNonNumericProviderIdentifier_becomesInvariantDeadWithoutAttempt() {
        Fixture fixture = createFixture(false, false);
        EncryptedProviderUserId invalid = providerIdCipher.encrypt("not-a-number");
        jdbcTemplate.update(
                "UPDATE social_account SET provider_user_id_ciphertext = ?, encryption_key_version = ? WHERE id = ?",
                invalid.ciphertext(),
                invalid.keyVersion(),
                socialAccountId);
        KakaoUnlinkClaim claim = claimFor(fixture.taskId());

        KakaoUnlinkReservation reservation = transactionService.reserveAttempt(claim);

        KakaoUnlinkTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(reservation.outcome()).isEqualTo(KakaoUnlinkReservation.Outcome.TERMINAL);
        assertThat(task.getLastErrorType()).isEqualTo(KakaoUnlinkTaskErrorType.INVARIANT);
        assertThat(task.getAttemptCount()).isZero();
    }

    @Test
    void twelfthReservation_retryableResultBecomesDeadWithoutThirteenthCall() {
        Fixture fixture = createFixture(false, false);
        jdbcTemplate.update(
                "UPDATE kakao_unlink_task SET attempt_count = 11 WHERE id = ?", fixture.taskId());
        KakaoUnlinkClaim claim = claimFor(fixture.taskId());
        KakaoUnlinkAttempt attempt = transactionService.reserveAttempt(claim).attempt();

        resultService.apply(
                attempt,
                new KakaoAdminUnlinkResult(KakaoAdminUnlinkDisposition.RETRYABLE, 503, null, null));

        KakaoUnlinkTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(task.getAttemptCount()).isEqualTo(12);
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.DEAD);
        assertThat(task.getLastErrorType()).isEqualTo(KakaoUnlinkTaskErrorType.ATTEMPT_EXHAUSTED);
    }

    @Test
    void eleventhReservation_retryableResultReturnsPendingAndClearsClaim() {
        Fixture fixture = createFixture(false, false);
        jdbcTemplate.update(
                "UPDATE kakao_unlink_task SET attempt_count = 10 WHERE id = ?", fixture.taskId());
        KakaoUnlinkClaim claim = claimFor(fixture.taskId());
        KakaoUnlinkAttempt attempt = transactionService.reserveAttempt(claim).attempt();

        resultService.apply(
                attempt,
                new KakaoAdminUnlinkResult(KakaoAdminUnlinkDisposition.RETRYABLE, 503, null, null));

        KakaoUnlinkTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(task.getAttemptCount()).isEqualTo(11);
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PENDING);
        assertThat(task.getClaimToken()).isNull();
        assertThat(task.getLeaseExpiresAt()).isNull();
    }

    @Test
    void twelfthReservationCrash_reclaimBecomesDeadWithoutHttpCall() {
        Fixture fixture = createFixture(false, false);
        jdbcTemplate.update(
                "UPDATE kakao_unlink_task SET attempt_count = 11 WHERE id = ?", fixture.taskId());
        KakaoUnlinkClaim oldClaim = claimFor(fixture.taskId());

        KakaoUnlinkReservation twelfthReservation = transactionService.reserveAttempt(oldClaim);
        assertThat(twelfthReservation.outcome()).isEqualTo(KakaoUnlinkReservation.Outcome.RESERVED);
        assertThat(taskRepository.findById(fixture.taskId()).orElseThrow().getAttemptCount())
                .isEqualTo(12);

        jdbcTemplate.update(
                "UPDATE kakao_unlink_task SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                fixture.taskId());
        KakaoUnlinkClaim newClaim = claimFor(fixture.taskId());
        KakaoUnlinkReservation exhausted = transactionService.reserveAttempt(newClaim);

        KakaoUnlinkTask task = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(newClaim.claimToken()).isNotEqualTo(oldClaim.claimToken());
        assertThat(exhausted.outcome()).isEqualTo(KakaoUnlinkReservation.Outcome.TERMINAL);
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.DEAD);
        assertThat(task.getAttemptCount()).isEqualTo(12);
        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(task.getClaimToken()).isNull();
        assertThat(task.getLeaseExpiresAt()).isNull();
        verify(adminApiClient, never()).unlink(anyLong());
    }

    @Test
    void configurationFailure_keepsRemainingClaimUntilLeaseRecoveryAfterResume() {
        Fixture first = createFixture(false, false);
        Fixture second = createFixture(false, false);
        List<KakaoUnlinkClaim> claims = claimService.claimBatch();
        KakaoUnlinkClaim firstClaim =
                claims.stream()
                        .filter(claim -> claim.taskId().equals(first.taskId()))
                        .findFirst()
                        .orElseThrow();
        KakaoUnlinkClaim secondClaim =
                claims.stream()
                        .filter(claim -> claim.taskId().equals(second.taskId()))
                        .findFirst()
                        .orElseThrow();
        KakaoUnlinkTask secondBefore = taskRepository.findById(second.taskId()).orElseThrow();
        String secondToken = secondBefore.getClaimToken();
        LocalDateTime secondLease = secondBefore.getLeaseExpiresAt();
        int secondAttemptCount = secondBefore.getAttemptCount();
        KakaoUnlinkAttempt firstAttempt = transactionService.reserveAttempt(firstClaim).attempt();

        resultService.applyConfigurationFailure(
                firstAttempt,
                new KakaoAdminUnlinkResult(
                        KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION, 401, -401, null));

        KakaoUnlinkTask firstAfter = taskRepository.findById(first.taskId()).orElseThrow();
        KakaoUnlinkTask secondAfter = taskRepository.findById(second.taskId()).orElseThrow();
        assertThat(firstAfter.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.DEAD);
        assertThat(firstAfter.getLastErrorType()).isEqualTo(KakaoUnlinkTaskErrorType.CONFIGURATION);
        assertThat(
                        controlRepository
                                .findById(KakaoUnlinkWorkerControl.SINGLETON_ID)
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(KakaoUnlinkWorkerControlStatus.CONFIGURATION_BLOCKED);
        assertThat(secondAfter.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PROCESSING);
        assertThat(secondAfter.getClaimToken()).isEqualTo(secondToken);
        assertThat(secondAfter.getLeaseExpiresAt()).isEqualTo(secondLease);
        assertThat(secondAfter.getAttemptCount()).isEqualTo(secondAttemptCount);
        assertThat(secondAfter.getCompletedAt()).isNull();
        verify(adminApiClient, never()).unlink(anyLong());

        jdbcTemplate.update(
                "UPDATE kakao_unlink_task SET lease_expires_at = UTC_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",
                second.taskId());
        assertThat(claimService.claimBatch()).isEmpty();
        resumeService.resumeConfigurationTasks(
                List.of(first.taskId()),
                "operator-1",
                KakaoUnlinkResumeReason.CONFIGURATION_VERIFIED);
        assertThat(taskRepository.findById(second.taskId()).orElseThrow().getStatus())
                .isEqualTo(KakaoUnlinkTaskStatus.PROCESSING);

        KakaoUnlinkClaim reclaimed = claimFor(second.taskId());
        assertThat(reclaimed.claimToken()).isNotEqualTo(secondClaim.claimToken());
    }

    @Test
    void actualTransactionProxies_commitReservationBeforeHttp() {
        Fixture fixture = createFixture(false, false);
        when(adminApiClient.unlink(fixture.kakaoUserId()))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            assertThat(
                                            jdbcTemplate.queryForObject(
                                                    "SELECT attempt_count FROM kakao_unlink_task WHERE id = ?",
                                                    Integer.class,
                                                    fixture.taskId()))
                                    .isOne();
                            assertThat(
                                            jdbcTemplate.queryForObject(
                                                    "SELECT status FROM kakao_unlink_task WHERE id = ?",
                                                    String.class,
                                                    fixture.taskId()))
                                    .isEqualTo("PROCESSING");
                            return new KakaoAdminUnlinkResult(
                                    KakaoAdminUnlinkDisposition.SUCCESS, 200, null, null);
                        });
        KakaoUnlinkWorker worker =
                new KakaoUnlinkWorker(
                        claimService, transactionService, adminApiClient, resultService);

        worker.runBatch();

        verify(adminApiClient).unlink(fixture.kakaoUserId());
        assertThat(taskRepository.findById(fixture.taskId()).orElseThrow().getStatus())
                .isEqualTo(KakaoUnlinkTaskStatus.SUCCEEDED);
    }

    @Test
    void duePendingQuery_usesProvidedDatabaseNowAtMicrosecondBoundary() {
        Fixture fixture = createFixture(false, false);
        jdbcTemplate.update(
                "UPDATE kakao_unlink_task SET next_attempt_at = UTC_TIMESTAMP(6) + INTERVAL 1 SECOND WHERE id = ?",
                fixture.taskId());
        LocalDateTime nextAttemptAt =
                taskRepository.findById(fixture.taskId()).orElseThrow().getNextAttemptAt();
        LocalDateTime databaseNow = nextAttemptAt.minusNanos(1_000);

        assertThat(findDuePending(databaseNow)).doesNotContain(fixture.taskId());
        assertThat(findDuePending(databaseNow.plusNanos(2_000))).contains(fixture.taskId());
    }

    @Test
    void expiredProcessingQuery_andReclaimUseSameDatabaseNowAtMicrosecondBoundary() {
        Fixture fixture = createFixture(false, false);
        KakaoUnlinkClaim originalClaim = claimFor(fixture.taskId());
        jdbcTemplate.update(
                "UPDATE kakao_unlink_task SET lease_expires_at = UTC_TIMESTAMP(6) + INTERVAL 1 SECOND WHERE id = ?",
                fixture.taskId());
        LocalDateTime leaseExpiresAt =
                taskRepository.findById(fixture.taskId()).orElseThrow().getLeaseExpiresAt();
        LocalDateTime databaseNow = leaseExpiresAt.minusNanos(1_000);

        assertThat(findExpiredProcessing(databaseNow)).doesNotContain(fixture.taskId());
        String replacementToken = UUID.randomUUID().toString();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            LocalDateTime reclaimNow = databaseNow.plusNanos(2_000);
                            KakaoUnlinkTask task =
                                    taskRepository
                                            .findExpiredProcessingForUpdate(reclaimNow, 10)
                                            .stream()
                                            .filter(
                                                    candidate ->
                                                            candidate
                                                                    .getId()
                                                                    .equals(fixture.taskId()))
                                            .findFirst()
                                            .orElseThrow();
                            task.reclaim(
                                    replacementToken,
                                    "boundary-test",
                                    reclaimNow,
                                    reclaimNow.plusMinutes(2));
                        });

        KakaoUnlinkTask reclaimed = taskRepository.findById(fixture.taskId()).orElseThrow();
        assertThat(reclaimed.getClaimToken()).isEqualTo(replacementToken);
        assertThat(reclaimed.getClaimToken()).isNotEqualTo(originalClaim.claimToken());
        assertThat(reclaimed.getClaimedAt()).isEqualTo(databaseNow.plusNanos(2_000));
    }

    @Test
    void resumeConfigurationTasks_normalizesDuplicatesAndRestoresAllTasksAtomically() {
        Fixture first = createFixture(false, false);
        Fixture second = createFixture(false, false);
        markConfigurationBlocked(List.of(first.taskId(), second.taskId()));

        assertThat(
                        resumeService.resumeConfigurationTasks(
                                List.of(second.taskId(), first.taskId(), second.taskId()),
                                "operator-1",
                                KakaoUnlinkResumeReason.ADMIN_KEY_CORRECTED))
                .isEqualTo(2);

        assertResumed(first.taskId());
        assertResumed(second.taskId());
        assertThat(
                        controlRepository
                                .findById(KakaoUnlinkWorkerControl.SINGLETON_ID)
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(KakaoUnlinkWorkerControlStatus.ACTIVE);
        assertThatThrownBy(
                        () ->
                                resumeService.resumeConfigurationTasks(
                                        List.of(first.taskId(), second.taskId()),
                                        "operator-1",
                                        KakaoUnlinkResumeReason.ADMIN_KEY_CORRECTED))
                .isInstanceOf(KakaoUnlinkResumeInvariantException.class);
        assertThat(taskRepository.findById(first.taskId()).orElseThrow().getRetryCycle()).isOne();
    }

    @Test
    void resumeConfigurationTasks_whenAnyConfigurationTaskIsOmitted_rollsBackEveryTask() {
        Fixture first = createFixture(false, false);
        Fixture second = createFixture(false, false);
        markConfigurationBlocked(List.of(first.taskId(), second.taskId()));
        ResumeTaskState firstBefore = resumeTaskState(first.taskId());
        ResumeTaskState secondBefore = resumeTaskState(second.taskId());

        assertThatThrownBy(
                        () ->
                                resumeService.resumeConfigurationTasks(
                                        List.of(first.taskId()),
                                        "operator-1",
                                        KakaoUnlinkResumeReason.CONFIGURATION_VERIFIED))
                .isInstanceOf(KakaoUnlinkResumeInvariantException.class);

        assertResumeRejected(first.taskId(), firstBefore);
        assertResumeRejected(second.taskId(), secondBefore);
        assertThat(
                        controlRepository
                                .findById(KakaoUnlinkWorkerControl.SINGLETON_ID)
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(KakaoUnlinkWorkerControlStatus.CONFIGURATION_BLOCKED);
    }

    @Test
    void resumeConfigurationTasks_whenUnknownIdIsAdded_rollsBackEveryTask() {
        Fixture first = createFixture(false, false);
        Fixture second = createFixture(false, false);
        markConfigurationBlocked(List.of(first.taskId(), second.taskId()));
        ResumeTaskState firstBefore = resumeTaskState(first.taskId());
        ResumeTaskState secondBefore = resumeTaskState(second.taskId());

        assertThatThrownBy(
                        () ->
                                resumeService.resumeConfigurationTasks(
                                        List.of(first.taskId(), second.taskId(), Long.MAX_VALUE),
                                        "operator-1",
                                        KakaoUnlinkResumeReason.CONFIGURATION_VERIFIED))
                .isInstanceOf(KakaoUnlinkResumeInvariantException.class);

        assertResumeRejected(first.taskId(), firstBefore);
        assertResumeRejected(second.taskId(), secondBefore);
        assertThat(
                        controlRepository
                                .findById(KakaoUnlinkWorkerControl.SINGLETON_ID)
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(KakaoUnlinkWorkerControlStatus.CONFIGURATION_BLOCKED);
    }

    private List<Long> findDuePending(LocalDateTime databaseNow) {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status ->
                                taskRepository.findDuePendingForUpdate(databaseNow, 10).stream()
                                        .map(KakaoUnlinkTask::getId)
                                        .toList());
    }

    private List<Long> findExpiredProcessing(LocalDateTime databaseNow) {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status ->
                                taskRepository
                                        .findExpiredProcessingForUpdate(databaseNow, 10)
                                        .stream()
                                        .map(KakaoUnlinkTask::getId)
                                        .toList());
    }

    private void markConfigurationBlocked(List<Long> taskIds) {
        jdbcTemplate.update(
                "UPDATE kakao_unlink_worker_control SET status = 'CONFIGURATION_BLOCKED', blocked_at = UTC_TIMESTAMP(6), blocked_reason = 'CONFIGURATION' WHERE id = ?",
                KakaoUnlinkWorkerControl.SINGLETON_ID);
        taskIds.forEach(
                taskId ->
                        jdbcTemplate.update(
                                "UPDATE kakao_unlink_task SET status = 'DEAD', last_error_type = 'CONFIGURATION', completed_at = UTC_TIMESTAMP(6) WHERE id = ?",
                                taskId));
    }

    private void assertResumed(Long taskId) {
        KakaoUnlinkTask task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PENDING);
        assertThat(task.getRetryCycle()).isOne();
        assertThat(task.getAttemptCount()).isZero();
        assertThat(task.getLastAttemptAt()).isNull();
        assertThat(task.getClaimToken()).isNull();
        assertThat(task.getLeaseExpiresAt()).isNull();
        assertThat(task.getCompletedAt()).isNull();
    }

    private ResumeTaskState resumeTaskState(Long taskId) {
        KakaoUnlinkTask task = taskRepository.findById(taskId).orElseThrow();
        return new ResumeTaskState(
                task.getStatus(),
                task.getLastErrorType(),
                task.getRetryCycle(),
                task.getAttemptCount(),
                task.getLastAttemptAt(),
                task.getCompletedAt());
    }

    private void assertResumeRejected(Long taskId, ResumeTaskState expected) {
        assertThat(resumeTaskState(taskId)).isEqualTo(expected);
    }

    private KakaoUnlinkClaim claimFor(Long taskId) {
        return claimService.claimBatch().stream()
                .filter(claim -> claim.taskId().equals(taskId))
                .findFirst()
                .orElseThrow();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release locked task");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding task lock", exception);
        }
    }

    private Fixture createFixture(boolean alreadyUnlinked, boolean withProfileImage) {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status -> {
                            String suffix = UUID.randomUUID().toString().replace("-", "");
                            User user =
                                    User.createSocial(
                                            "worker-test",
                                            null,
                                            null,
                                            "010" + suffix.substring(0, 8),
                                            "worker" + suffix.substring(0, 8),
                                            null,
                                            true,
                                            true,
                                            false,
                                            null,
                                            List.of());
                            if (withProfileImage) {
                                user.changeProfileImageKey("profiles/" + suffix + ".jpg");
                            }
                            user.requestWithdrawal(WithdrawalReason.SELF, TASK_TIME);
                            userRepository.save(user);
                            String providerUserKey = suffix.repeat(2);
                            long kakaoUserId = PROVIDER_ID_SEQUENCE.incrementAndGet();
                            EncryptedProviderUserId encryptedProviderUserId =
                                    providerIdCipher.encrypt(Long.toString(kakaoUserId));
                            SocialAccount account =
                                    SocialAccount.createLinked(
                                            user,
                                            SocialProvider.KAKAO,
                                            Long.toString(kakaoUserId),
                                            providerUserKey,
                                            1,
                                            encryptedProviderUserId,
                                            TASK_TIME);
                            account.markUnlinkPending(TASK_TIME.plusSeconds(1));
                            if (alreadyUnlinked) {
                                account.markUnlinked(TASK_TIME.plusSeconds(2));
                            }
                            socialAccountRepository.save(account);
                            KakaoUnlinkTask task =
                                    taskRepository.save(
                                            KakaoUnlinkTask.pending(account, 1L, TASK_TIME));
                            userId = user.getId();
                            socialAccountId = account.getId();
                            createdUserIds.add(userId);
                            createdSocialAccountIds.add(socialAccountId);
                            return new Fixture(
                                    task.getId(),
                                    kakaoUserId,
                                    providerUserKey,
                                    withProfileImage ? user.getProfileImageKey() : null);
                        });
    }

    private record Fixture(
            Long taskId, long kakaoUserId, String providerUserKey, String profileImageKey) {}

    private record ResumeTaskState(
            KakaoUnlinkTaskStatus status,
            KakaoUnlinkTaskErrorType lastErrorType,
            int retryCycle,
            int attemptCount,
            LocalDateTime lastAttemptAt,
            LocalDateTime completedAt) {}
}
