package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.gather.gather.domain.auth.dto.LoginRequest;
import com.gather.gather.domain.auth.dto.PasswordResetRequest;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class PasswordResetIntegrationTest {

    private static final String PHONE_NUMBER = "01096660201";
    private static final String EMAIL = "reset-apply@example.com";
    private static final String OLD_PASSWORD = "oldpass123";
    private static final String NEW_PASSWORD = "newpass123";
    private static final String REFRESH_TOKEN_HASH = "f".repeat(64);

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private AuthService authService;
    @Autowired private PasswordResetTokenCodec passwordResetTokenCodec;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    @MockitoSpyBean private RefreshTokenRepository refreshTokenRepository;

    private Long userId;
    private String rawToken;

    @BeforeEach
    void setUp() {
        rawToken = passwordResetTokenCodec.generateToken();
        userId = null;
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            if (userId == null) {
                                return;
                            }
                            passwordResetTokenRepository.deleteAllByUserId(userId);
                            refreshTokenRepository.deleteAllByUserId(userId);
                            socialAccountRepository
                                    .findIdentitySnapshotsByUserIdAndProvider(
                                            userId, SocialProvider.KAKAO)
                                    .forEach(
                                            snapshot ->
                                                    socialAccountRepository.deleteById(
                                                            snapshot.id()));
                            userRepository.deleteById(userId);
                        });
    }

    @Test
    @DisplayName("재설정에 성공하면 비밀번호가 바뀌고 재설정 토큰과 Refresh Token이 모두 사라진다")
    void resetPassword_changesPasswordAndRevokesSessions() {
        saveEmailUser();
        saveRefreshToken();
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));

        passwordResetService.resetPassword(request(rawToken, NEW_PASSWORD, NEW_PASSWORD));

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, user.getPassword())).isFalse();
        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(REFRESH_TOKEN_HASH)).isEmpty();
    }

    @Test
    @DisplayName("재설정 후에는 새 비밀번호로만 로그인할 수 있다")
    void resetPassword_allowsLoginWithNewPasswordOnly() {
        saveEmailUser();
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));

        passwordResetService.resetPassword(request(rawToken, NEW_PASSWORD, NEW_PASSWORD));

        assertThat(authService.login(new LoginRequest(EMAIL, NEW_PASSWORD)).accessToken())
                .isNotBlank();
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, OLD_PASSWORD)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_LOGIN));
    }

    @Test
    @DisplayName("비밀번호 확인이 다르면 아무것도 바꾸지 않는다")
    void resetPassword_confirmMismatch_changesNothing() {
        saveEmailUser();
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));

        assertErrorCode(
                request(rawToken, NEW_PASSWORD, NEW_PASSWORD + "x"), ErrorCode.PASSWORD_MISMATCH);

        assertPasswordUnchangedAndTokenKept();
    }

    @Test
    @DisplayName("정책을 위반한 비밀번호는 아무것도 바꾸지 않는다")
    void resetPassword_policyViolation_changesNothing() {
        saveEmailUser();
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));

        assertErrorCode(request(rawToken, "short", "short"), ErrorCode.VALIDATION_ERROR);
        assertErrorCode(request(rawToken, "with space", "with space"), ErrorCode.VALIDATION_ERROR);

        assertPasswordUnchangedAndTokenKept();
    }

    @Test
    @DisplayName("형식이 어긋난 토큰과 없는 토큰은 모두 INVALID로 거부한다")
    void resetPassword_malformedOrUnknownToken_isRejected() {
        saveEmailUser();
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));

        assertErrorCode(
                request("short-token", NEW_PASSWORD, NEW_PASSWORD),
                ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        assertErrorCode(
                request(null, NEW_PASSWORD, NEW_PASSWORD), ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        assertErrorCode(
                request(passwordResetTokenCodec.generateToken(), NEW_PASSWORD, NEW_PASSWORD),
                ErrorCode.PASSWORD_RESET_TOKEN_INVALID);

        assertPasswordUnchangedAndTokenKept();
    }

    @Test
    @DisplayName("만료된 토큰은 EXPIRED로 거부한다")
    void resetPassword_expiredToken_isRejected() {
        saveEmailUser();
        saveResetToken(rawToken, LocalDateTime.now(clock).minusMinutes(1));

        assertErrorCode(
                request(rawToken, NEW_PASSWORD, NEW_PASSWORD),
                ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);

        assertPasswordUnchangedAndTokenKept();
    }

    @Test
    @DisplayName("이미 사용해 삭제된 토큰은 INVALID로 거부한다")
    void resetPassword_consumedToken_isRejected() {
        saveEmailUser();
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));
        passwordResetService.resetPassword(request(rawToken, NEW_PASSWORD, NEW_PASSWORD));

        assertErrorCode(
                request(rawToken, NEW_PASSWORD, NEW_PASSWORD),
                ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }

    @Test
    @DisplayName("카카오 전용 계정의 토큰으로는 비밀번호를 만들 수 없다")
    void resetPassword_kakaoOnlyAccount_isRejected() {
        saveKakaoUser();
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));

        assertErrorCode(
                request(rawToken, NEW_PASSWORD, NEW_PASSWORD),
                ErrorCode.PASSWORD_RESET_TOKEN_INVALID);

        assertThat(userRepository.findById(userId).orElseThrow().getPassword()).isNull();
    }

    @Test
    @DisplayName("탈퇴 진행 중 계정은 INVALID로 거부한다")
    void resetPassword_inactiveAccount_isRejected() {
        saveEmailUser();
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                userRepository
                                        .findById(userId)
                                        .orElseThrow()
                                        .requestWithdrawal(
                                                WithdrawalReason.SELF, LocalDateTime.now(clock)));

        assertErrorCode(
                request(rawToken, NEW_PASSWORD, NEW_PASSWORD),
                ErrorCode.PASSWORD_RESET_TOKEN_INVALID);

        assertPasswordUnchangedAndTokenKept();
    }

    @Test
    @DisplayName("비밀번호 없이 이메일만 있는 계정은 INVALID로 거부한다")
    void resetPassword_credentialInvariantMismatch_isRejected() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            User user = socialUser();
                            ReflectionTestUtils.setField(user, "email", EMAIL);
                            userId = userRepository.save(user).getId();
                        });
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));

        assertErrorCode(
                request(rawToken, NEW_PASSWORD, NEW_PASSWORD),
                ErrorCode.PASSWORD_RESET_TOKEN_INVALID);

        assertThat(userRepository.findById(userId).orElseThrow().getPassword()).isNull();
    }

    @Test
    @DisplayName("Refresh Token 삭제가 실패하면 비밀번호 변경과 토큰 삭제도 롤백한다")
    void resetPassword_rollsBackEverythingWhenSessionRevocationFails() {
        saveEmailUser();
        saveRefreshToken();
        saveResetToken(rawToken, LocalDateTime.now(clock).plusMinutes(10));
        doThrow(new IllegalStateException("refresh token 삭제 실패"))
                .when(refreshTokenRepository)
                .deleteAllByUserId(userId);

        assertThatThrownBy(
                        () ->
                                passwordResetService.resetPassword(
                                        request(rawToken, NEW_PASSWORD, NEW_PASSWORD)))
                .isInstanceOf(IllegalStateException.class);

        assertPasswordUnchangedAndTokenKept();
        // 뒷정리에서 같은 메서드를 다시 호출하므로 스텁을 되돌린다.
        Mockito.reset(refreshTokenRepository);
        assertThat(refreshTokenRepository.findByTokenHash(REFRESH_TOKEN_HASH)).isPresent();
    }

    private void assertPasswordUnchangedAndTokenKept() {
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, user.getPassword())).isTrue();
        assertThat(passwordResetTokenRepository.findByUserId(userId)).isPresent();
    }

    private void assertErrorCode(PasswordResetRequest request, ErrorCode errorCode) {
        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private PasswordResetRequest request(String token, String password, String passwordConfirm) {
        return new PasswordResetRequest(token, password, passwordConfirm);
    }

    private void saveResetToken(String token, LocalDateTime expiresAt) {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                passwordResetTokenRepository.save(
                                        PasswordResetToken.issue(
                                                userRepository.findById(userId).orElseThrow(),
                                                passwordResetTokenCodec.validateAndHash(token),
                                                expiresAt,
                                                expiresAt.minusMinutes(10))));
    }

    private void saveRefreshToken() {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                refreshTokenRepository.save(
                                        RefreshToken.create(
                                                REFRESH_TOKEN_HASH,
                                                userRepository.findById(userId).orElseThrow(),
                                                LocalDateTime.now(clock).plusDays(14))));
    }

    private void saveEmailUser() {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                userId =
                                        userRepository
                                                .save(
                                                        User.create(
                                                                "재설정회원",
                                                                LocalDate.of(2000, 1, 1),
                                                                Gender.FEMALE,
                                                                PHONE_NUMBER,
                                                                EMAIL,
                                                                passwordEncoder.encode(
                                                                        OLD_PASSWORD),
                                                                "재설정적용",
                                                                null,
                                                                true,
                                                                true,
                                                                false,
                                                                null,
                                                                List.of()))
                                                .getId());
    }

    private void saveKakaoUser() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            User user = userRepository.save(socialUser());
                            userId = user.getId();
                            socialAccountRepository.save(
                                    SocialAccount.createLinked(
                                            user,
                                            SocialProvider.KAKAO,
                                            "legacy-reset-2",
                                            "d".repeat(64),
                                            1,
                                            new EncryptedProviderUserId("ciphertext-reset-2", 1),
                                            LocalDateTime.now(clock)));
                        });
    }

    private User socialUser() {
        return User.createSocial(
                "카카오회원",
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                PHONE_NUMBER,
                "재설정카카오2",
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
