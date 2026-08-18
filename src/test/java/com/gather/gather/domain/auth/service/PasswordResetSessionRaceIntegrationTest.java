package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.dto.LoginRequest;
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
import java.util.List;
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
 * 비밀번호 재설정과 세션 발급 경쟁을 실제 MySQL 잠금으로 검증한다.
 *
 * <p>재설정이 커밋된 뒤 옛 비밀번호나 옛 Refresh Token으로 새 세션이 살아남으면 안 된다.
 */
@SpringBootTest
class PasswordResetSessionRaceIntegrationTest {

    private static final String PHONE_NUMBER = "01096660301";
    private static final String EMAIL = "reset-race@example.com";
    private static final String OLD_PASSWORD = "oldpass123";
    private static final String NEW_PASSWORD = "newpass123";
    private static final long BLOCKED_TIMEOUT_MILLISECONDS = 500;

    @Autowired private PasswordResetService passwordResetService;
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
                                                    "경쟁회원",
                                                    LocalDate.of(2000, 1, 1),
                                                    Gender.FEMALE,
                                                    PHONE_NUMBER,
                                                    EMAIL,
                                                    passwordEncoder.encode(OLD_PASSWORD),
                                                    "재설정경쟁",
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
    @DisplayName("재설정이 먼저 커밋되면 옛 비밀번호 로그인은 잠금 해제 후 INVALID_LOGIN으로 실패한다")
    void resetCommitFirst_blocksOldPasswordLoginFromCreatingSession() throws Exception {
        AtomicReference<Future<ErrorCode>> loginFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordResetService.resetPassword(
                                    new PasswordResetRequest(
                                            rawResetToken, NEW_PASSWORD, NEW_PASSWORD));
                            Future<ErrorCode> login =
                                    executorService.submit(() -> loginWithOldPassword());
                            assertBlocked(login);
                            loginFuture.set(login);
                        });

        assertThat(loginFuture.get().get(10, TimeUnit.SECONDS)).isEqualTo(ErrorCode.INVALID_LOGIN);
        assertThat(refreshTokenCount()).isZero();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, currentPassword())).isTrue();
    }

    @Test
    @DisplayName("옛 비밀번호 로그인이 먼저 커밋되면 재설정이 그 Refresh Token까지 삭제한다")
    void loginCommitFirst_isRevokedByLaterReset() throws Exception {
        AtomicReference<Future<Boolean>> resetFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            authService.login(new LoginRequest(EMAIL, OLD_PASSWORD));
                            Future<Boolean> reset = executorService.submit(this::resetPassword);
                            assertBlocked(reset);
                            resetFuture.set(reset);
                        });

        assertThat(resetFuture.get().get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(refreshTokenCount()).isZero();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, currentPassword())).isTrue();
    }

    @Test
    @DisplayName("재발급이 먼저 커밋되면 재설정이 새로 발급된 Refresh Token까지 삭제한다")
    void reissueCommitFirst_isRevokedByLaterReset() throws Exception {
        AtomicReference<Future<Boolean>> resetFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            authService.reissue(rawRefreshToken);
                            Future<Boolean> reset = executorService.submit(this::resetPassword);
                            assertBlocked(reset);
                            resetFuture.set(reset);
                        });

        assertThat(resetFuture.get().get(10, TimeUnit.SECONDS)).isTrue();
        assertThat(refreshTokenCount()).isZero();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, currentPassword())).isTrue();
    }

    @Test
    @DisplayName("재설정이 먼저 커밋되면 옛 Refresh Token 재발급은 실패한다")
    void resetCommitFirst_blocksReissueWithOldRefreshToken() throws Exception {
        AtomicReference<Future<ErrorCode>> reissueFuture = new AtomicReference<>();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordResetService.resetPassword(
                                    new PasswordResetRequest(
                                            rawResetToken, NEW_PASSWORD, NEW_PASSWORD));
                            Future<ErrorCode> reissue = executorService.submit(this::reissue);
                            assertBlocked(reissue);
                            reissueFuture.set(reissue);
                        });

        assertThat(reissueFuture.get().get(10, TimeUnit.SECONDS))
                .isIn(ErrorCode.INVALID_TOKEN, ErrorCode.REVOKED_TOKEN);
        assertThat(refreshTokenCount()).isZero();
    }

    private ErrorCode loginWithOldPassword() {
        try {
            authService.login(new LoginRequest(EMAIL, OLD_PASSWORD));
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private ErrorCode reissue() {
        try {
            authService.reissue(rawRefreshToken);
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private boolean resetPassword() {
        passwordResetService.resetPassword(
                new PasswordResetRequest(rawResetToken, NEW_PASSWORD, NEW_PASSWORD));
        return true;
    }

    private void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(BLOCKED_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
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
