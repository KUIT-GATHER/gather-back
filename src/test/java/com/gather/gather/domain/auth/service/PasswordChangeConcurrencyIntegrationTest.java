package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.dto.LoginRequest;
import com.gather.gather.domain.auth.dto.PasswordChangeRequest;
import com.gather.gather.domain.auth.dto.PasswordResetRequest;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

/**
 * 로그인 상태 비밀번호 변경과 다른 credential 흐름의 경쟁을 실제 MySQL 잠금으로 검증한다.
 *
 * <p>변경이 커밋된 뒤 옛 비밀번호 세션이나 옛 재설정 토큰이 살아남으면 안 된다.
 */
@SpringBootTest
class PasswordChangeConcurrencyIntegrationTest {

    private static final String PHONE_NUMBER = "01096660901";
    private static final String EMAIL = "change-race@example.com";
    private static final String OLD_PASSWORD = "oldpass123";
    private static final String NEW_PASSWORD = "newpass123";
    private static final String OTHER_PASSWORD = "otherpass1";
    private static final long BLOCKED_TIMEOUT_MILLISECONDS = 500;

    @Autowired private PasswordChangeService passwordChangeService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordResetTokenCleanupService passwordResetTokenCleanupService;
    @Autowired private AuthService authService;
    @Autowired private TokenProvider tokenProvider;
    @Autowired private PasswordResetTokenCodec passwordResetTokenCodec;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private Long userId;
    private String rawResetToken;
    private String rawRefreshToken;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(2);
        rawResetToken = passwordResetTokenCodec.generateToken();
        rawRefreshToken = tokenProvider.generateToken();
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            User user =
                                    userRepository.save(
                                            User.create(
                                                    "변경경쟁회원",
                                                    LocalDate.of(2000, 1, 1),
                                                    Gender.FEMALE,
                                                    PHONE_NUMBER,
                                                    EMAIL,
                                                    passwordEncoder.encode(OLD_PASSWORD),
                                                    "변경경쟁",
                                                    null,
                                                    true,
                                                    true,
                                                    false,
                                                    null,
                                                    List.of()));
                            userId = user.getId();
                            LocalDateTime now = LocalDateTime.now(clock);
                            passwordResetTokenRepository.save(
                                    PasswordResetToken.issue(
                                            user,
                                            passwordResetTokenCodec.validateAndHash(rawResetToken),
                                            now.plusMinutes(10),
                                            now));
                            refreshTokenRepository.save(
                                    RefreshToken.create(
                                            tokenProvider.hashToken(rawRefreshToken),
                                            user,
                                            now.plusDays(14)));
                        });
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
                            userRepository.deleteById(userId);
                        });
    }

    @Test
    @DisplayName("같은 현재 비밀번호로 동시에 변경하면 정확히 하나만 성공한다")
    void changePassword_concurrently_succeedsOnce() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<ErrorCode> first =
                executorService.submit(() -> changeAfterBarrier(NEW_PASSWORD, ready, start));
        Future<ErrorCode> second =
                executorService.submit(() -> changeAfterBarrier(OTHER_PASSWORD, ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        // 성공은 errorCode가 없는 결과라 null을 허용하는 리스트가 필요하다.
        List<ErrorCode> results =
                Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        assertThat(results).filteredOn(Objects::isNull).hasSize(1);
        assertThat(results)
                .filteredOn(Objects::nonNull)
                .containsExactly(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        assertThat(passwordEncoder.matches(OLD_PASSWORD, currentPassword())).isFalse();
        assertThat(resetTokenCount()).isZero();
        assertThat(refreshTokenCount()).isZero();
    }

    @Test
    @DisplayName("변경이 먼저 커밋되면 옛 비밀번호 로그인은 잠금 해제 후 INVALID_LOGIN으로 실패한다")
    void changeCommitFirst_blocksOldPasswordLogin() throws Exception {
        AtomicReference<Future<ErrorCode>> loginFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordChangeService.changePassword(userId, request(NEW_PASSWORD));
                            Future<ErrorCode> login = executorService.submit(this::loginWithOld);
                            assertBlocked(login);
                            loginFuture.set(login);
                        });

        assertThat(loginFuture.get().get(20, TimeUnit.SECONDS)).isEqualTo(ErrorCode.INVALID_LOGIN);
        assertThat(refreshTokenCount()).isZero();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, currentPassword())).isTrue();
    }

    @Test
    @DisplayName("옛 비밀번호 로그인이 먼저 커밋되면 변경이 그 Refresh Token까지 삭제한다")
    void loginCommitFirst_isRevokedByLaterChange() throws Exception {
        AtomicReference<Future<ErrorCode>> changeFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            authService.login(new LoginRequest(EMAIL, OLD_PASSWORD));
                            Future<ErrorCode> change =
                                    executorService.submit(() -> change(NEW_PASSWORD));
                            assertBlocked(change);
                            changeFuture.set(change);
                        });

        assertThat(changeFuture.get().get(20, TimeUnit.SECONDS)).isNull();
        assertThat(refreshTokenCount()).isZero();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, currentPassword())).isTrue();
    }

    @Test
    @DisplayName("변경이 먼저 커밋되면 진행 중이던 재설정 토큰 사용은 실패한다")
    void changeCommitFirst_invalidatesPendingResetToken() throws Exception {
        AtomicReference<Future<ErrorCode>> resetFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordChangeService.changePassword(userId, request(NEW_PASSWORD));
                            Future<ErrorCode> reset = executorService.submit(this::resetPassword);
                            assertBlocked(reset);
                            resetFuture.set(reset);
                        });

        assertThat(resetFuture.get().get(20, TimeUnit.SECONDS))
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        assertThat(resetTokenCount()).isZero();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, currentPassword())).isTrue();
    }

    @Test
    @DisplayName("재설정이 먼저 커밋되면 옛 현재 비밀번호로는 변경할 수 없다")
    void resetCommitFirst_blocksChangeWithStaleCurrentPassword() throws Exception {
        AtomicReference<Future<ErrorCode>> changeFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordResetService.resetPassword(
                                    new PasswordResetRequest(
                                            rawResetToken, OTHER_PASSWORD, OTHER_PASSWORD));
                            Future<ErrorCode> change =
                                    executorService.submit(() -> change(NEW_PASSWORD));
                            assertBlocked(change);
                            changeFuture.set(change);
                        });

        assertThat(changeFuture.get().get(20, TimeUnit.SECONDS))
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        assertThat(passwordEncoder.matches(OTHER_PASSWORD, currentPassword())).isTrue();
    }

    @Test
    @DisplayName("만료 토큰 정리와 동시에 변경해도 교착 없이 재설정 토큰이 남지 않는다")
    void changePassword_racesWithResetTokenCleanup() throws Exception {
        expireResetToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<ErrorCode> change =
                executorService.submit(() -> changeAfterBarrier(NEW_PASSWORD, ready, start));
        Future<ErrorCode> cleanup =
                executorService.submit(
                        () -> {
                            ready.countDown();
                            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                            try {
                                passwordResetTokenCleanupService.cleanupExpiredTokens();
                                return null;
                            } catch (BusinessException exception) {
                                return exception.getErrorCode();
                            }
                        });
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(change.get(20, TimeUnit.SECONDS)).isNull();
        assertThat(cleanup.get(20, TimeUnit.SECONDS)).isNull();
        assertThat(resetTokenCount()).isZero();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, currentPassword())).isTrue();
    }

    private ErrorCode changeAfterBarrier(
            String newPassword, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return change(newPassword);
    }

    private ErrorCode change(String newPassword) {
        try {
            passwordChangeService.changePassword(userId, request(newPassword));
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private ErrorCode loginWithOld() {
        try {
            authService.login(new LoginRequest(EMAIL, OLD_PASSWORD));
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private ErrorCode resetPassword() {
        try {
            passwordResetService.resetPassword(
                    new PasswordResetRequest(rawResetToken, OTHER_PASSWORD, OTHER_PASSWORD));
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    // expires_at > created_at CHECK 제약이 있어 발급 시각까지 함께 과거로 옮겨야 만료 상태를 만들 수 있다.
    private void expireResetToken() {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbcTemplate.update(
                "update password_reset_token set created_at = ?, expires_at = ? where user_id = ?",
                now.minusMinutes(20),
                now.minusMinutes(1),
                userId);
    }

    private PasswordChangeRequest request(String newPassword) {
        return new PasswordChangeRequest(OLD_PASSWORD, newPassword, newPassword);
    }

    private void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(BLOCKED_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private long resetTokenCount() {
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
}
