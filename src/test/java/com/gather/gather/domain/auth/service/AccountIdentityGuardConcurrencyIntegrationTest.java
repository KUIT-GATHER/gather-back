package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.AccountIdentityGuardRepository;
import com.gather.gather.domain.auth.repository.AccountRejoinBlockRepository;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(AccountIdentityGuardConcurrencyIntegrationTest.ConcurrencyTestConfiguration.class)
class AccountIdentityGuardConcurrencyIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-31T05:25:56.123456Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
    private static final String PHONE = "01087654321";
    private static final String SIGNUP_EMAIL = "guard-signup@example.com";
    private static final UUID PHONE_VERIFICATION_ID =
            UUID.fromString("5c5d5db1-4187-43d0-8580-672307994878");
    private static final UUID EMAIL_VERIFICATION_ID =
            UUID.fromString("98fa88ef-bbeb-4928-a202-7885197b3774");

    @Autowired private AuthService authService;
    @Autowired private AccountTerminationService terminationService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private AccountRejoinBlockRepository blockRepository;
    @Autowired private AccountIdentityGuardRepository guardRepository;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private GuardLockCoordinator coordinator;
    @MockitoBean private PhoneVerificationRequirementService phoneVerificationRequirementService;

    private Fixture fixture;

    @AfterEach
    void cleanUp() {
        coordinator.release();
        if (fixture == null) {
            return;
        }
        RejoinBlockIdentifier identifier = identifierHasher.hashPhone(PHONE);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            jdbcTemplate.update(
                                    "delete from account_rejoin_block where identifier_type = 'PHONE' and identifier_hash = ?",
                                    identifier.hash());
                            jdbcTemplate.update(
                                    "delete from account_identity_guard where identity_type = 'PHONE' and key_version = ? and identity_hash = ?",
                                    identifier.keyVersion(),
                                    identifier.hash());
                            emailVerificationRepository.deleteById(fixture.verificationId());
                            userRepository.findAll().stream()
                                    .filter(user -> PHONE.equals(user.getPhoneNumber()))
                                    .forEach(userRepository::delete);
                            userRepository
                                    .findById(fixture.userId())
                                    .ifPresent(userRepository::delete);
                            regionRepository.deleteById(fixture.regionId());
                        });
    }

    @Test
    void signupFirstFailsDuplicateThenWithdrawalCompletesWithoutDeadlock() throws Exception {
        fixture = createFixture();
        coordinator.arm("signup-first");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> signup =
                    executor.submit(
                            () -> {
                                runNamed("signup-first", () -> authService.signup(signupRequest()));
                                return null;
                            });
            coordinator.awaitPaused();
            Future<AccountTerminationResult> withdrawal =
                    executor.submit(
                            () ->
                                    runNamed(
                                            "withdrawal-second",
                                            () ->
                                                    terminationService.terminate(
                                                            fixture.userId(),
                                                            WithdrawalReason.SELF)));
            coordinator.awaitEntered("withdrawal-second");
            coordinator.release();

            assertBusinessError(signup, ErrorCode.DUPLICATE_PHONE_NUMBER);
            assertThat(withdrawal.get(5, TimeUnit.SECONDS).outcome())
                    .isEqualTo(AccountTerminationOutcome.COMPLETED);
            assertInvariant();
        } finally {
            coordinator.release();
            executor.shutdownNow();
        }
    }

    @Test
    void withdrawalFirstCreatesBlockThenSignupFailsWithDomainError() throws Exception {
        fixture = createFixture();
        coordinator.arm("withdrawal-first");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<AccountTerminationResult> withdrawal =
                    executor.submit(
                            () ->
                                    runNamed(
                                            "withdrawal-first",
                                            () ->
                                                    terminationService.terminate(
                                                            fixture.userId(),
                                                            WithdrawalReason.SELF)));
            coordinator.awaitPaused();
            Future<Void> signup =
                    executor.submit(
                            () -> {
                                runNamed(
                                        "signup-second", () -> authService.signup(signupRequest()));
                                return null;
                            });
            coordinator.awaitEntered("signup-second");
            coordinator.release();

            assertThat(withdrawal.get(5, TimeUnit.SECONDS).outcome())
                    .isEqualTo(AccountTerminationOutcome.COMPLETED);
            assertBusinessError(signup, ErrorCode.ACCOUNT_REJOIN_BLOCKED);
            assertInvariant();
        } finally {
            coordinator.release();
            executor.shutdownNow();
        }
    }

    private Fixture createFixture() {
        return transactionTemplate()
                .execute(
                        status -> {
                            Region region =
                                    regionRepository.save(
                                            Region.create(
                                                    "guard-test", 2, "guard-test-code", null));
                            User user =
                                    userRepository.save(
                                            User.create(
                                                    "기존회원",
                                                    LocalDate.of(2000, 1, 1),
                                                    Gender.MALE,
                                                    PHONE,
                                                    "guard-existing@example.com",
                                                    "encoded-password",
                                                    "기존회원",
                                                    null,
                                                    true,
                                                    true,
                                                    false,
                                                    region,
                                                    List.of(PostingCategory.WELFARE)));
                            EmailVerification verification =
                                    EmailVerification.create(
                                            SIGNUP_EMAIL,
                                            EMAIL_VERIFICATION_ID.toString(),
                                            "a".repeat(64),
                                            NOW.plusDays(1),
                                            NOW);
                            verification.verify(NOW);
                            emailVerificationRepository.save(verification);
                            return new Fixture(user.getId(), region.getId(), verification.getId());
                        });
    }

    private SignupRequest signupRequest() {
        return new SignupRequest(
                "신규회원",
                LocalDate.of(2001, 1, 1),
                Gender.FEMALE,
                PHONE,
                PHONE_VERIFICATION_ID,
                SIGNUP_EMAIL,
                EMAIL_VERIFICATION_ID,
                "password1",
                "password1",
                "신규회원",
                null,
                fixture.regionId(),
                List.of(PostingCategory.WELFARE),
                true,
                true,
                false);
    }

    private void assertInvariant() {
        RejoinBlockIdentifier identifier = identifierHasher.hashPhone(PHONE);
        assertThat(
                        blockRepository.existsByIdentifierTypeAndIdentifierHashAndExpiresAtAfter(
                                identifier.type(), identifier.hash(), NOW))
                .isTrue();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from users where phone_number = ? and status = 'ACTIVE'",
                                Long.class,
                                PHONE))
                .isZero();
        assertThat(userRepository.findById(fixture.userId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);
    }

    private void assertBusinessError(Future<?> future, ErrorCode expected) {
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOfSatisfying(
                        ExecutionException.class,
                        exception -> {
                            assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
                            assertThat(((BusinessException) exception.getCause()).getErrorCode())
                                    .isEqualTo(expected);
                        });
    }

    private <T> T runNamed(String name, ThrowingSupplier<T> action) throws Exception {
        String originalName = Thread.currentThread().getName();
        Thread.currentThread().setName(name);
        try {
            return action.get();
        } finally {
            Thread.currentThread().setName(originalName);
        }
    }

    private void runNamed(String name, ThrowingRunnable action) throws Exception {
        runNamed(
                name,
                () -> {
                    action.run();
                    return null;
                });
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private record Fixture(Long userId, Long regionId, Long verificationId) {}

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @TestConfiguration
    static class ConcurrencyTestConfiguration {

        @Bean
        GuardLockCoordinator guardLockCoordinator() {
            return new GuardLockCoordinator();
        }

        @Bean
        @Primary
        AccountIdentityGuardService controllableAccountIdentityGuardService(
                AccountIdentityGuardRepository repository,
                RejoinBlockIdentifierHasher hasher,
                GuardLockCoordinator coordinator) {
            return new ControllableAccountIdentityGuardService(repository, hasher, coordinator);
        }

        @Bean
        @Primary
        Clock guardConcurrencyClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    static class ControllableAccountIdentityGuardService extends AccountIdentityGuardService {

        private final GuardLockCoordinator coordinator;

        ControllableAccountIdentityGuardService(
                AccountIdentityGuardRepository repository,
                RejoinBlockIdentifierHasher hasher,
                GuardLockCoordinator coordinator) {
            super(repository, hasher);
            this.coordinator = coordinator;
        }

        @Override
        public RejoinBlockIdentifier lockPhone(String phoneNumber, LocalDateTime now) {
            coordinator.entered(Thread.currentThread().getName());
            RejoinBlockIdentifier identifier = super.lockPhone(phoneNumber, now);
            coordinator.pauseIfArmed(Thread.currentThread().getName());
            return identifier;
        }
    }

    static final class GuardLockCoordinator {

        private final Map<String, CountDownLatch> entered = new ConcurrentHashMap<>();
        private volatile String pauseThread;
        private volatile CountDownLatch paused = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        void arm(String threadName) {
            pauseThread = threadName;
            paused = new CountDownLatch(1);
            release = new CountDownLatch(1);
            entered.clear();
        }

        void entered(String threadName) {
            entered.computeIfAbsent(threadName, ignored -> new CountDownLatch(1)).countDown();
        }

        void pauseIfArmed(String threadName) {
            if (!threadName.equals(pauseThread)) {
                return;
            }
            paused.countDown();
            await(release);
        }

        void awaitPaused() {
            await(paused);
        }

        void awaitEntered(String threadName) {
            await(entered.computeIfAbsent(threadName, ignored -> new CountDownLatch(1)));
        }

        void release() {
            release.countDown();
        }

        private void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시성 테스트 latch 대기 시간이 초과되었습니다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("동시성 테스트 latch 대기가 중단되었습니다.", exception);
            }
        }
    }
}
