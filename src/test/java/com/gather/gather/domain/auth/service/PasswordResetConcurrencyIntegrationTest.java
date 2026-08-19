package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.dto.PasswordResetAuthorityRequest;
import com.gather.gather.domain.auth.dto.PasswordResetRequest;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 비밀번호 재설정 발급·사용과 탈퇴 경쟁을 실제 MySQL 잠금으로 검증한다. */
@SpringBootTest
class PasswordResetConcurrencyIntegrationTest {

    private static final String PHONE_NUMBER = "01096660601";
    private static final String EMAIL = "reset-concurrency@example.com";
    private static final String OLD_PASSWORD = "oldpass123";
    private static final String NEW_PASSWORD = "newpass123";
    private static final long BLOCKED_TIMEOUT_MILLISECONDS = 500;

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private AccountTerminationService accountTerminationService;
    @Autowired private PasswordResetTokenCodec passwordResetTokenCodec;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private Long userId;
    private final List<UUID> verificationIds = new ArrayList<>();
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(2);
        verificationIds.clear();
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                userId =
                                        userRepository
                                                .save(
                                                        User.create(
                                                                "동시재설정",
                                                                LocalDate.of(2000, 1, 1),
                                                                Gender.FEMALE,
                                                                PHONE_NUMBER,
                                                                EMAIL,
                                                                passwordEncoder.encode(
                                                                        OLD_PASSWORD),
                                                                "동시재설정",
                                                                null,
                                                                true,
                                                                true,
                                                                false,
                                                                null,
                                                                List.of()))
                                                .getId());
    }

    @AfterEach
    void cleanUp() throws Exception {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordResetTokenRepository.deleteAllByUserId(userId);
                            refreshTokenRepository.deleteAllByUserId(userId);
                            verificationIds.forEach(
                                    id ->
                                            phoneVerificationRepository
                                                    .findByVerificationId(id.toString())
                                                    .ifPresent(
                                                            phoneVerificationRepository::delete));
                            jdbcTemplate.update(
                                    "delete from account_rejoin_block where source_user_id = ?",
                                    userId);
                            userRepository.deleteById(userId);
                            jdbcTemplate.update(
                                    "delete from account_identity_guard where identity_hash = ?",
                                    identifierHasher.hashPhone(PHONE_NUMBER).hash());
                        });
    }

    @Test
    @DisplayName("같은 휴대폰 인증으로 동시에 발급하면 한 번만 성공하고 토큰도 하나만 남는다")
    void issueToken_sameVerificationConcurrently_succeedsOnce() throws Exception {
        UUID verificationId = saveVerifiedVerification();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<IssueAttempt> first =
                executorService.submit(() -> issueAfterBarrier(verificationId, ready, start));
        Future<IssueAttempt> second =
                executorService.submit(() -> issueAfterBarrier(verificationId, ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<IssueAttempt> attempts =
                List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        assertThat(attempts)
                .filteredOn(attempt -> attempt.errorCode() == null)
                .hasSize(1)
                .allSatisfy(attempt -> assertThat(attempt.rawToken()).isNotBlank());
        assertThat(attempts)
                .filteredOn(attempt -> attempt.errorCode() != null)
                .singleElement()
                .satisfies(
                        attempt ->
                                assertThat(attempt.errorCode())
                                        .isEqualTo(ErrorCode.PHONE_VERIFICATION_REQUIRED));
        assertThat(tokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 휴대폰 인증으로 동시에 발급하면 마지막 토큰만 유효하다")
    void issueToken_differentVerificationsConcurrently_keepsOnlyLatestToken() throws Exception {
        UUID firstVerificationId = saveVerifiedVerification();
        UUID secondVerificationId = saveVerifiedVerification();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<IssueAttempt> first =
                executorService.submit(() -> issueAfterBarrier(firstVerificationId, ready, start));
        Future<IssueAttempt> second =
                executorService.submit(() -> issueAfterBarrier(secondVerificationId, ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<IssueAttempt> attempts =
                List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        assertThat(attempts).allSatisfy(attempt -> assertThat(attempt.errorCode()).isNull());
        assertThat(tokenCount()).isEqualTo(1);

        String storedHash =
                passwordResetTokenRepository.findByUserId(userId).orElseThrow().getTokenHash();
        List<String> survivingTokens =
                attempts.stream()
                        .map(IssueAttempt::rawToken)
                        .filter(
                                token ->
                                        passwordResetTokenCodec
                                                .validateAndHash(token)
                                                .equals(storedHash))
                        .toList();
        assertThat(survivingTokens).hasSize(1);
        attempts.stream()
                .map(IssueAttempt::rawToken)
                .filter(token -> !survivingTokens.contains(token))
                .forEach(
                        staleToken ->
                                assertResetError(
                                        staleToken, ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
    }

    @Test
    @DisplayName("같은 재설정 토큰으로 동시에 요청하면 정확히 하나만 성공한다")
    void resetPassword_sameTokenConcurrently_succeedsOnce() throws Exception {
        String rawToken = issueTokenDirectly();
        saveRefreshToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<ErrorCode> first =
                executorService.submit(() -> resetAfterBarrier(rawToken, ready, start));
        Future<ErrorCode> second =
                executorService.submit(() -> resetAfterBarrier(rawToken, ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        // 성공은 errorCode가 없는 결과라 null을 허용하는 리스트가 필요하다.
        List<ErrorCode> results =
                Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        assertThat(results).filteredOn(Objects::isNull).hasSize(1);
        assertThat(results)
                .filteredOn(Objects::nonNull)
                .containsExactly(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, currentPassword())).isTrue();
        assertThat(tokenCount()).isZero();
        assertThat(refreshTokenCount()).isZero();
    }

    @Test
    @DisplayName("재발급이 먼저 커밋되면 진행 중이던 옛 토큰 사용은 실패한다")
    void reissueCommitFirst_invalidatesStaleTokenInFlight() throws Exception {
        String staleToken = issueTokenDirectly();
        UUID reissueVerificationId = saveVerifiedVerification();
        AtomicReference<Future<ErrorCode>> resetFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordResetService.issueToken(
                                    new PasswordResetAuthorityRequest(reissueVerificationId));
                            Future<ErrorCode> reset =
                                    executorService.submit(() -> reset(staleToken));
                            assertBlocked(reset);
                            resetFuture.set(reset);
                        });

        assertThat(resetFuture.get().get(20, TimeUnit.SECONDS))
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        assertThat(tokenCount()).isEqualTo(1);
        assertThat(passwordEncoder.matches(OLD_PASSWORD, currentPassword())).isTrue();
    }

    @Test
    @DisplayName("탈퇴가 먼저 커밋되면 재설정은 실패한다")
    void withdrawalCommitFirst_blocksPasswordReset() throws Exception {
        String rawToken = issueTokenDirectly();
        AtomicReference<Future<ErrorCode>> resetFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            accountTerminationService.terminate(userId, WithdrawalReason.SELF);
                            Future<ErrorCode> reset = executorService.submit(() -> reset(rawToken));
                            assertBlocked(reset);
                            resetFuture.set(reset);
                        });

        assertThat(resetFuture.get().get(20, TimeUnit.SECONDS))
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        assertThat(tokenCount()).isZero();
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("재설정이 먼저 커밋되면 이후 탈퇴가 재설정 credential을 남기지 않는다")
    void resetCommitFirst_isFollowedByWithdrawalWithoutStaleCredential() throws Exception {
        String rawToken = issueTokenDirectly();
        AtomicReference<Future<Boolean>> withdrawalFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordResetService.resetPassword(
                                    new PasswordResetRequest(rawToken, NEW_PASSWORD, NEW_PASSWORD));
                            Future<Boolean> withdrawal =
                                    executorService.submit(
                                            () -> {
                                                accountTerminationService.terminate(
                                                        userId, WithdrawalReason.SELF);
                                                return true;
                                            });
                            assertBlocked(withdrawal);
                            withdrawalFuture.set(withdrawal);
                        });

        assertThat(withdrawalFuture.get().get(20, TimeUnit.SECONDS)).isTrue();
        assertThat(tokenCount()).isZero();
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);
    }

    private IssueAttempt issueAfterBarrier(
            UUID verificationId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            return new IssueAttempt(
                    passwordResetService
                            .issueToken(new PasswordResetAuthorityRequest(verificationId))
                            .passwordResetToken(),
                    null);
        } catch (BusinessException exception) {
            return new IssueAttempt(null, exception.getErrorCode());
        }
    }

    private ErrorCode resetAfterBarrier(String rawToken, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return reset(rawToken);
    }

    private ErrorCode reset(String rawToken) {
        try {
            passwordResetService.resetPassword(
                    new PasswordResetRequest(rawToken, NEW_PASSWORD, NEW_PASSWORD));
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private void assertResetError(String rawToken, ErrorCode errorCode) {
        assertThat(reset(rawToken)).isEqualTo(errorCode);
    }

    private String issueTokenDirectly() {
        UUID verificationId = saveVerifiedVerification();
        return passwordResetService
                .issueToken(new PasswordResetAuthorityRequest(verificationId))
                .passwordResetToken();
    }

    private UUID saveVerifiedVerification() {
        UUID verificationId = UUID.randomUUID();
        verificationIds.add(verificationId);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            PhoneVerification verification =
                                    PhoneVerification.create(
                                            verificationId.toString(),
                                            PHONE_NUMBER,
                                            PhoneVerificationPurpose.RESET_PASSWORD,
                                            "GATHER-RESET0003",
                                            now.plusMinutes(5),
                                            now.minusMinutes(1));
                            verification.verify(now.minusMinutes(1));
                            phoneVerificationRepository.save(verification);
                        });
        return verificationId;
    }

    private void saveRefreshToken() {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                refreshTokenRepository.save(
                                        RefreshToken.create(
                                                "e".repeat(64),
                                                userRepository.findById(userId).orElseThrow(),
                                                LocalDateTime.now(clock).plusDays(14))));
    }

    private void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(BLOCKED_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private long tokenCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from password_reset_token where user_id = ?", Long.class, userId);
    }

    private long refreshTokenCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where user_id = ?", Long.class, userId);
    }

    private String currentPassword() {
        return jdbcTemplate.queryForObject(
                "select password from users where id = ?", String.class, userId);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private record IssueAttempt(String rawToken, ErrorCode errorCode) {}
}
