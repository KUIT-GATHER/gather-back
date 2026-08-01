package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.service.SocialSignupSessionService;
import com.gather.gather.domain.auth.repository.AccountRejoinBlockRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(AccountTerminationRaceIntegrationTest.RaceTestConfiguration.class)
class AccountTerminationRaceIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-31T05:25:56.123456Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    @Autowired private LockedTokenIssuanceService tokenIssuanceService;
    @Autowired private SocialSignupSessionService signupSessionService;
    @Autowired private AccountTerminationService terminationService;
    @Autowired private UserRepository userRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AccountRejoinBlockRepository blockRepository;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RaceCoordinator coordinator;

    private Long userId;
    private Long socialAccountId;

    @AfterEach
    void cleanUp() {
        coordinator.release();
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            if (socialAccountId != null) {
                                jdbcTemplate.update(
                                        "delete from kakao_unlink_task where social_account_id = ?",
                                        socialAccountId);
                                jdbcTemplate.update(
                                        "delete from social_signup_session where provider_user_key = ?",
                                        "d".repeat(64));
                                socialAccountRepository.deleteById(socialAccountId);
                            }
                            if (userId != null) {
                                refreshTokenRepository.deleteAllByUserId(userId);
                                userRepository.deleteById(userId);
                            }
                            jdbcTemplate.update(
                                    "delete from account_rejoin_block where source_user_id = ?",
                                    userId == null ? -1L : userId);
                            RejoinBlockIdentifier phone = identifierHasher.hashPhone("01076543210");
                            jdbcTemplate.update(
                                    "delete from account_identity_guard where identity_hash = ?",
                                    phone.hash());
                        });
    }

    @Test
    void tokenIssuanceCommitBeforeWithdrawalIsDeletedByWithdrawal() throws Exception {
        userId = transactionTemplate().execute(status -> userRepository.save(localUser()).getId());
        coordinator.arm("token-issue");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<TokenIssueResult> issue =
                    executor.submit(
                            () ->
                                    runNamed(
                                            "token-issue",
                                            () -> tokenIssuanceService.issue(userId)));
            coordinator.awaitPaused();
            Future<AccountTerminationResult> withdrawal =
                    executor.submit(
                            () -> terminationService.terminate(userId, WithdrawalReason.SELF));
            assertThatThrownBy(() -> withdrawal.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            coordinator.release();

            assertThat(issue.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(withdrawal.get(5, TimeUnit.SECONDS).outcome())
                    .isEqualTo(AccountTerminationOutcome.COMPLETED);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "select count(*) from refresh_token where user_id = ?",
                                    Long.class,
                                    userId))
                    .isZero();
            assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                    .isEqualTo(UserStatus.WITHDRAWN);
        } finally {
            coordinator.release();
            executor.shutdownNow();
        }
    }

    @Test
    void committedSignupSessionIsCancelledWhenWithdrawalRacesItsInsert() throws Exception {
        SocialFixture fixture = transactionTemplate().execute(status -> createSocialFixture());
        userId = fixture.userId();
        socialAccountId = fixture.socialAccountId();
        RejoinBlockIdentifier kakaoIdentifier =
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.KAKAO, "d".repeat(64), 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> issue =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                await(start);
                                return signupSessionService.issue(
                                        kakaoIdentifier,
                                        new EncryptedProviderUserId("session-ciphertext", 1));
                            });
            Future<AccountTerminationResult> withdrawal =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                await(start);
                                return terminationService.terminate(userId, WithdrawalReason.SELF);
                            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(withdrawal.get(5, TimeUnit.SECONDS).outcome())
                    .isEqualTo(AccountTerminationOutcome.ACCEPTED);
            assertSessionIssueResultIsPolicyOutcome(issue);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "select count(*) from social_signup_session where provider_user_key = ? and status = 'PENDING'",
                                    Long.class,
                                    kakaoIdentifier.hash()))
                    .isZero();
            assertThat(
                            blockRepository
                                    .existsByIdentifierTypeAndIdentifierHashAndExpiresAtAfter(
                                            AccountRejoinBlockIdentifierType.KAKAO,
                                            kakaoIdentifier.hash(),
                                            NOW))
                    .isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void assertSessionIssueResultIsPolicyOutcome(Future<String> issue) throws Exception {
        try {
            assertThat(issue.get(5, TimeUnit.SECONDS)).isNotBlank();
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) exception.getCause()).getErrorCode())
                    .isIn(
                            ErrorCode.ALREADY_REGISTERED,
                            ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED,
                            ErrorCode.ACCOUNT_REJOIN_BLOCKED);
        }
    }

    private User localUser() {
        return User.create(
                "회원",
                null,
                null,
                "01076543210",
                "token-race@example.com",
                "encoded-password",
                "토큰경쟁",
                null,
                true,
                true,
                false,
                null,
                List.of());
    }

    private SocialFixture createSocialFixture() {
        User user =
                userRepository.save(
                        User.createSocial(
                                "회원",
                                null,
                                null,
                                "01076543210",
                                "세션경쟁",
                                null,
                                true,
                                true,
                                false,
                                null,
                                List.of()));
        SocialAccount socialAccount =
                socialAccountRepository.save(
                        SocialAccount.createLinked(
                                user,
                                SocialProvider.KAKAO,
                                "987654321",
                                "d".repeat(64),
                                1,
                                new EncryptedProviderUserId("account-ciphertext", 1),
                                NOW.minusDays(1)));
        return new SocialFixture(user.getId(), socialAccount.getId());
    }

    private <T> T runNamed(String name, ThrowingSupplier<T> action) throws Exception {
        String original = Thread.currentThread().getName();
        Thread.currentThread().setName(name);
        try {
            return action.get();
        } finally {
            Thread.currentThread().setName(original);
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("race 시작 latch 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("race 시작 latch 대기가 중단되었습니다.", exception);
        }
    }

    private record SocialFixture(Long userId, Long socialAccountId) {}

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @TestConfiguration
    static class RaceTestConfiguration {

        @Bean
        RaceCoordinator raceCoordinator() {
            return new RaceCoordinator();
        }

        @Bean
        @Primary
        TokenIssuer controllableTokenIssuer(
                TokenProvider tokenProvider,
                RefreshTokenRepository refreshTokenRepository,
                RaceCoordinator coordinator) {
            return new ControllableTokenIssuer(tokenProvider, refreshTokenRepository, coordinator);
        }

        @Bean
        @Primary
        AccountRejoinBlockService controllableRejoinBlockService(
                AccountRejoinBlockRepository repository,
                RejoinBlockIdentifierHasher hasher,
                RaceCoordinator coordinator) {
            return new ControllableRejoinBlockService(repository, hasher, coordinator);
        }

        @Bean
        @Primary
        Clock raceClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    static class ControllableTokenIssuer extends TokenIssuer {

        private final RaceCoordinator coordinator;

        ControllableTokenIssuer(
                TokenProvider tokenProvider,
                RefreshTokenRepository refreshTokenRepository,
                RaceCoordinator coordinator) {
            super(tokenProvider, refreshTokenRepository);
            this.coordinator = coordinator;
        }

        @Override
        public TokenIssueResult issue(User user) {
            TokenIssueResult result = super.issue(user);
            coordinator.pauseIfArmed(Thread.currentThread().getName());
            return result;
        }
    }

    static class ControllableRejoinBlockService extends AccountRejoinBlockService {

        private final RaceCoordinator coordinator;

        ControllableRejoinBlockService(
                AccountRejoinBlockRepository repository,
                RejoinBlockIdentifierHasher hasher,
                RaceCoordinator coordinator) {
            super(repository, hasher);
            this.coordinator = coordinator;
        }

        @Override
        public boolean isBlocked(RejoinBlockIdentifier identifier, LocalDateTime now) {
            boolean blocked = super.isBlocked(identifier, now);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                coordinator.pauseIfArmed(Thread.currentThread().getName());
            }
            return blocked;
        }
    }

    static final class RaceCoordinator {

        private volatile String armedThread;
        private volatile CountDownLatch paused = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        void arm(String threadName) {
            armedThread = threadName;
            paused = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        void pauseIfArmed(String threadName) {
            if (!threadName.equals(armedThread)) {
                return;
            }
            paused.countDown();
            await(release);
        }

        void awaitPaused() {
            await(paused);
        }

        void release() {
            release.countDown();
        }

        private void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("race 테스트 latch 대기 시간이 초과되었습니다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("race 테스트 latch 대기가 중단되었습니다.", exception);
            }
        }
    }
}
