package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.dto.LoginRequest;
import com.gather.gather.domain.auth.dto.PasswordChangeRequest;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 로그인 상태 비밀번호 변경을 실제 DB로 검증한다. */
@SpringBootTest
class PasswordChangeIntegrationTest {

    private static final String PHONE_NUMBER = "01096660801";
    private static final String EMAIL = "password-change@example.com";
    private static final String OLD_PASSWORD = "oldpass123";
    private static final String NEW_PASSWORD = "newpass123";

    @Autowired private PasswordChangeService passwordChangeService;
    @Autowired private AuthService authService;
    @Autowired private TokenProvider tokenProvider;
    @Autowired private PasswordResetTokenCodec passwordResetTokenCodec;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = null;
    }

    @AfterEach
    void cleanUp() {
        if (userId == null) {
            return;
        }
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordResetTokenRepository.deleteAllByUserId(userId);
                            refreshTokenRepository.deleteAllByUserId(userId);
                            jdbcTemplate.update(
                                    "delete from social_account where user_id = ?", userId);
                            userRepository.deleteById(userId);
                        });
    }

    @Test
    @DisplayName("이메일 계정은 비밀번호가 바뀌고 재설정 토큰·Refresh Token이 모두 사라진다")
    void changePassword_succeedsAndRevokesCredentials() {
        saveEmailUser();
        saveResetToken();
        saveRefreshToken();

        passwordChangeService.changePassword(userId, request(OLD_PASSWORD, NEW_PASSWORD));

        assertThat(passwordEncoder.matches(NEW_PASSWORD, currentPassword())).isTrue();
        assertThat(resetTokenCount()).isZero();
        assertThat(refreshTokenCount()).isZero();
    }

    @Test
    @DisplayName("변경 후 옛 비밀번호 로그인은 실패하고 새 비밀번호 로그인은 성공한다")
    void changePassword_switchesLoginCredential() {
        saveEmailUser();

        passwordChangeService.changePassword(userId, request(OLD_PASSWORD, NEW_PASSWORD));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, OLD_PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_LOGIN);
        assertThat(authService.login(new LoginRequest(EMAIL, NEW_PASSWORD)).accessToken())
                .isNotBlank();
    }

    @Test
    @DisplayName("카카오 전용 계정은 409로 거부되고 비밀번호가 생기지 않는다")
    void changePassword_rejectsKakaoOnlyAccount() {
        saveKakaoOnlyUser();

        assertChangeFails(
                request(OLD_PASSWORD, NEW_PASSWORD), ErrorCode.PASSWORD_CHANGE_NOT_AVAILABLE);
        assertThat(currentPassword()).isNull();
    }

    @Test
    @DisplayName("비밀번호만 있고 이메일이 없는 계정은 500으로 실패한다")
    void changePassword_rejectsPartialCredential() {
        saveEmailUser();
        saveResetToken();
        saveRefreshToken();
        jdbcTemplate.update("update users set email = null where id = ?", userId);

        assertChangeFails(request(OLD_PASSWORD, NEW_PASSWORD), ErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(passwordEncoder.matches(OLD_PASSWORD, currentPassword())).isTrue();
        assertThat(resetTokenCount()).isEqualTo(1);
        assertThat(refreshTokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 비밀번호가 다르면 400으로 실패하고 세션이 유지된다")
    void changePassword_rejectsWrongCurrentPassword() {
        saveEmailUser();
        saveResetToken();
        saveRefreshToken();

        assertChangeFails(request("wrongpass", NEW_PASSWORD), ErrorCode.CURRENT_PASSWORD_MISMATCH);
        assertThat(passwordEncoder.matches(OLD_PASSWORD, currentPassword())).isTrue();
        assertThat(resetTokenCount()).isEqualTo(1);
        assertThat(refreshTokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("정지된 계정은 403 SUSPENDED_USER로 거부되고 credential이 그대로 남는다")
    void changePassword_rejectsSuspendedUser() {
        saveEmailUser();
        saveResetToken();
        saveRefreshToken();
        updateStatus(UserStatus.SUSPENDED);

        assertChangeFails(request(OLD_PASSWORD, NEW_PASSWORD), ErrorCode.SUSPENDED_USER);
        assertUnchangedCredentials();
    }

    @Test
    @DisplayName("탈퇴 처리 중인 계정은 403 WITHDRAWAL_PENDING_USER로 거부된다")
    void changePassword_rejectsWithdrawalPendingUser() {
        saveEmailUser();
        saveResetToken();
        saveRefreshToken();
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                userRepository
                                        .findById(userId)
                                        .orElseThrow()
                                        .requestWithdrawal(
                                                WithdrawalReason.SELF, LocalDateTime.now(clock)));

        assertChangeFails(request(OLD_PASSWORD, NEW_PASSWORD), ErrorCode.WITHDRAWAL_PENDING_USER);
        assertUnchangedCredentials();
    }

    @Test
    @DisplayName("탈퇴한 계정은 403 WITHDRAWN_USER로 거부된다")
    void changePassword_rejectsWithdrawnUser() {
        saveEmailUser();
        saveResetToken();
        saveRefreshToken();
        updateStatus(UserStatus.WITHDRAWN);

        assertChangeFails(request(OLD_PASSWORD, NEW_PASSWORD), ErrorCode.WITHDRAWN_USER);
        assertUnchangedCredentials();
    }

    private void assertChangeFails(PasswordChangeRequest request, ErrorCode errorCode) {
        assertThatThrownBy(() -> passwordChangeService.changePassword(userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }

    private void assertUnchangedCredentials() {
        assertThat(passwordEncoder.matches(OLD_PASSWORD, currentPassword())).isTrue();
        assertThat(resetTokenCount()).isEqualTo(1);
        assertThat(refreshTokenCount()).isEqualTo(1);
    }

    private void updateStatus(UserStatus status) {
        jdbcTemplate.update("update users set status = ? where id = ?", status.name(), userId);
    }

    private void saveEmailUser() {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                userId =
                                        userRepository
                                                .save(
                                                        User.create(
                                                                "변경회원",
                                                                LocalDate.of(2000, 1, 1),
                                                                Gender.FEMALE,
                                                                PHONE_NUMBER,
                                                                EMAIL,
                                                                passwordEncoder.encode(
                                                                        OLD_PASSWORD),
                                                                "변경회원",
                                                                null,
                                                                true,
                                                                true,
                                                                false,
                                                                null,
                                                                List.of()))
                                                .getId());
    }

    private void saveKakaoOnlyUser() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            User user =
                                    userRepository.save(
                                            User.createSocial(
                                                    "카카오회원",
                                                    LocalDate.of(2000, 1, 1),
                                                    Gender.MALE,
                                                    PHONE_NUMBER,
                                                    "변경카카오",
                                                    null,
                                                    true,
                                                    true,
                                                    false,
                                                    null,
                                                    List.of()));
                            userId = user.getId();
                            socialAccountRepository.save(
                                    SocialAccount.createLinked(
                                            user,
                                            SocialProvider.KAKAO,
                                            "legacy-change-1",
                                            "c".repeat(64),
                                            1,
                                            new EncryptedProviderUserId("ciphertext-change", 1),
                                            LocalDateTime.now(clock)));
                        });
    }

    private void saveResetToken() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            passwordResetTokenRepository.save(
                                    PasswordResetToken.issue(
                                            userRepository.findById(userId).orElseThrow(),
                                            passwordResetTokenCodec.validateAndHash(
                                                    passwordResetTokenCodec.generateToken()),
                                            now.plusMinutes(10),
                                            now));
                        });
    }

    private void saveRefreshToken() {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                refreshTokenRepository.save(
                                        RefreshToken.create(
                                                tokenProvider.hashToken(
                                                        tokenProvider.generateToken()),
                                                userRepository.findById(userId).orElseThrow(),
                                                LocalDateTime.now(clock).plusDays(14))));
    }

    private PasswordChangeRequest request(String currentPassword, String password) {
        return new PasswordChangeRequest(currentPassword, password, password);
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
