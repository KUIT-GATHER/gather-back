package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.dto.PasswordResetRequest;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class PasswordResetTokenCleanupIntegrationTest {

    private static final String EXPIRED_PHONE_NUMBER = "01096660501";
    private static final String VALID_PHONE_NUMBER = "01096660502";
    private static final String NEW_PASSWORD = "newpass123";

    @Autowired private PasswordResetTokenCleanupService cleanupService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordResetTokenCodec passwordResetTokenCodec;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private Long expiredUserId;
    private Long validUserId;
    private String expiredRawToken;
    private String validRawToken;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newSingleThreadExecutor();
        expiredRawToken = passwordResetTokenCodec.generateToken();
        validRawToken = passwordResetTokenCodec.generateToken();
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            User expiredOwner =
                                    userRepository.save(
                                            user(
                                                    EXPIRED_PHONE_NUMBER,
                                                    "reset-cleanup-expired@example.com",
                                                    "파기만료"));
                            User validOwner =
                                    userRepository.save(
                                            user(
                                                    VALID_PHONE_NUMBER,
                                                    "reset-cleanup-valid@example.com",
                                                    "파기유효"));
                            expiredUserId = expiredOwner.getId();
                            validUserId = validOwner.getId();
                            passwordResetTokenRepository.save(
                                    PasswordResetToken.issue(
                                            expiredOwner,
                                            passwordResetTokenCodec.validateAndHash(
                                                    expiredRawToken),
                                            now.minusMinutes(1),
                                            now.minusMinutes(11)));
                            passwordResetTokenRepository.save(
                                    PasswordResetToken.issue(
                                            validOwner,
                                            passwordResetTokenCodec.validateAndHash(validRawToken),
                                            now.plusMinutes(10),
                                            now));
                        });
    }

    @AfterEach
    void cleanUp() throws Exception {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordResetTokenRepository.deleteAllByUserId(expiredUserId);
                            passwordResetTokenRepository.deleteAllByUserId(validUserId);
                            userRepository.deleteById(expiredUserId);
                            userRepository.deleteById(validUserId);
                        });
    }

    @Test
    @DisplayName("만료된 토큰만 파기하고 유효한 토큰은 남기며 반복 실행은 멱등하다")
    void cleanupExpiredTokens_deletesOnlyExpiredTokensAndIsIdempotent() {
        int deleted = cleanupService.cleanupExpiredTokens();

        assertThat(deleted).isEqualTo(1);
        assertThat(passwordResetTokenRepository.findByUserId(expiredUserId)).isEmpty();
        assertThat(passwordResetTokenRepository.findByUserId(validUserId)).isPresent();
        assertThat(cleanupService.cleanupExpiredTokens()).isZero();
    }

    @Test
    @DisplayName("만료 시각이 현재 시각과 같은 토큰도 파기 대상이다")
    void cleanupExpiredTokens_deletesTokenExpiringExactlyNow() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            passwordResetTokenRepository.deleteAllByUserId(validUserId);
                            passwordResetTokenRepository.save(
                                    PasswordResetToken.issue(
                                            userRepository.findById(validUserId).orElseThrow(),
                                            passwordResetTokenCodec.validateAndHash(validRawToken),
                                            now,
                                            now.minusMinutes(10)));
                        });

        assertThat(cleanupService.cleanupExpiredTokens()).isEqualTo(2);
        assertThat(passwordResetTokenRepository.findByUserId(validUserId)).isEmpty();
    }

    @Test
    @DisplayName("만료 토큰은 파기 전에는 EXPIRED, 파기 후에는 INVALID로 응답한다")
    void resetPassword_expiredTokenBecomesInvalidAfterCleanup() {
        assertErrorCode(expiredRawToken, ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);

        assertThat(cleanupService.cleanupExpiredTokens()).isEqualTo(1);

        assertErrorCode(expiredRawToken, ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }

    @Test
    @DisplayName("재설정이 유효한 토큰을 잠근 동안에도 파기 배치는 대기 없이 끝난다")
    void cleanupExpiredTokens_doesNotWaitForLockedValidToken() throws Exception {
        Future<Integer> cleanup =
                transactionTemplate()
                        .execute(
                                status -> {
                                    passwordResetTokenRepository
                                            .findByTokenHashForUpdate(
                                                    passwordResetTokenCodec.validateAndHash(
                                                            validRawToken))
                                            .orElseThrow();
                                    return executorService.submit(
                                            () -> cleanupService.cleanupExpiredTokens());
                                });

        assertThat(cleanup.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(passwordResetTokenRepository.findByUserId(validUserId)).isPresent();
        assertThat(passwordResetTokenRepository.findByUserId(expiredUserId)).isEmpty();
    }

    private void assertErrorCode(String rawToken, ErrorCode errorCode) {
        assertThatThrownBy(
                        () ->
                                passwordResetService.resetPassword(
                                        new PasswordResetRequest(
                                                rawToken, NEW_PASSWORD, NEW_PASSWORD)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private User user(String phoneNumber, String email, String nickname) {
        return User.create(
                "파기회원",
                LocalDate.of(2000, 1, 1),
                Gender.FEMALE,
                phoneNumber,
                email,
                passwordEncoder.encode("oldpass123"),
                nickname,
                null,
                true,
                true,
                false,
                null,
                List.of());
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
