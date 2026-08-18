package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.gather.gather.domain.auth.dto.PasswordResetAuthorityRequest;
import com.gather.gather.domain.auth.dto.PasswordResetTokenResponse;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class PasswordResetAuthorityIntegrationTest {

    private static final String PHONE_NUMBER = "01096660101";

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private UUID verificationId;
    private Long userId;

    @BeforeEach
    void setUp() {
        verificationId = UUID.randomUUID();
        userId = null;
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            if (userId != null) {
                                passwordResetTokenRepository.deleteAllByUserId(userId);
                                socialAccountRepository
                                        .findIdentitySnapshotsByUserIdAndProvider(
                                                userId, SocialProvider.KAKAO)
                                        .forEach(
                                                snapshot ->
                                                        socialAccountRepository.deleteById(
                                                                snapshot.id()));
                            }
                            phoneVerificationRepository
                                    .findByVerificationId(verificationId.toString())
                                    .ifPresent(phoneVerificationRepository::delete);
                            if (userId != null) {
                                userRepository.deleteById(userId);
                            }
                        });
    }

    @Test
    @DisplayName("EMAIL 계정은 재설정 토큰을 받고 hash만 저장되며 휴대폰 인증은 소비된다")
    void issueToken_emailAccount_issuesHashedTokenAndConsumesVerification() {
        saveEmailUser();
        saveVerifiedVerification(PhoneVerificationPurpose.RESET_PASSWORD);

        PasswordResetTokenResponse response = issueToken();

        assertThat(response.passwordResetToken()).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
        PasswordResetToken saved = passwordResetTokenRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(response.passwordResetToken()));
        assertThat(passwordResetTokenRepository.findByTokenHash(response.passwordResetToken()))
                .isEmpty();
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plusMinutes(10));
        assertThat(saved.getCreatedAt())
                .isBetween(
                        LocalDateTime.now(clock).minusMinutes(1),
                        LocalDateTime.now(clock).plusMinutes(1));
        // DB 왕복 시 저장된 값은 마이크로초로 반올림되므로, 저장 전 원본값과는 밀리초 오차 이내로 비교한다.
        assertThat(response.expiresAt()).isCloseTo(saved.getExpiresAt(), within(1, ChronoUnit.MILLIS));
        assertThat(consumedAt()).isNotNull();
    }

    @Test
    @DisplayName("재발급하면 직전 토큰은 사라지고 사용자당 한 행만 남는다")
    void issueToken_reissue_keepsOnlyLatestToken() {
        saveEmailUser();
        saveVerifiedVerification(PhoneVerificationPurpose.RESET_PASSWORD);
        String firstToken = issueToken().passwordResetToken();
        String firstHash = sha256Hex(firstToken);

        UUID firstVerificationId = verificationId;
        verificationId = UUID.randomUUID();
        saveVerifiedVerification(PhoneVerificationPurpose.RESET_PASSWORD);
        String secondToken = issueToken().passwordResetToken();

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(passwordResetTokenRepository.findByTokenHash(firstHash)).isEmpty();
        assertThat(passwordResetTokenRepository.findByTokenHash(sha256Hex(secondToken)))
                .isPresent();
        assertThat(passwordResetTokenRepository.findAll())
                .filteredOn(token -> token.getUser().getId().equals(userId))
                .hasSize(1);
        deleteVerification(firstVerificationId);
    }

    @Test
    @DisplayName("카카오 전용 계정은 토큰 없이 409로 거부하고 휴대폰 인증은 소비한다")
    void issueToken_kakaoOnlyAccount_isRejectedAndConsumesVerification() {
        saveKakaoUser();
        saveVerifiedVerification(PhoneVerificationPurpose.RESET_PASSWORD);

        assertErrorCode(ErrorCode.PASSWORD_RESET_NOT_AVAILABLE);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(consumedAt()).isNotNull();
    }

    @Test
    @DisplayName("계정이 없으면 404로 거부하고 휴대폰 인증은 소비한다")
    void issueToken_unknownAccount_isRejectedAndConsumesVerification() {
        saveVerifiedVerification(PhoneVerificationPurpose.RESET_PASSWORD);

        assertErrorCode(ErrorCode.ACCOUNT_NOT_FOUND);

        assertThat(consumedAt()).isNotNull();
    }

    @Test
    @DisplayName("탈퇴 진행 중 계정은 404로 거부하고 휴대폰 인증은 소비한다")
    void issueToken_inactiveAccount_isRejectedAndConsumesVerification() {
        saveEmailUser();
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                userRepository
                                        .findById(userId)
                                        .orElseThrow()
                                        .requestWithdrawal(
                                                WithdrawalReason.SELF, LocalDateTime.now(clock)));
        saveVerifiedVerification(PhoneVerificationPurpose.RESET_PASSWORD);

        assertErrorCode(ErrorCode.ACCOUNT_NOT_FOUND);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(consumedAt()).isNotNull();
    }

    @Test
    @DisplayName("비밀번호 없이 이메일만 있는 계정은 404로 거부하고 휴대폰 인증은 소비한다")
    void issueToken_credentialInvariantMismatch_isRejectedAndConsumesVerification() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            User user = socialUser();
                            ReflectionTestUtils.setField(
                                    user, "email", "reset-invariant@example.com");
                            userId = userRepository.save(user).getId();
                        });
        saveVerifiedVerification(PhoneVerificationPurpose.RESET_PASSWORD);

        assertErrorCode(ErrorCode.ACCOUNT_NOT_FOUND);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(consumedAt()).isNotNull();
    }

    @Test
    @DisplayName("다른 목적의 휴대폰 인증은 소비하지 않고 거부한다")
    void issueToken_purposeMismatch_isRejectedWithoutConsumption() {
        saveEmailUser();
        saveVerifiedVerification(PhoneVerificationPurpose.FIND_ACCOUNT);

        assertErrorCode(ErrorCode.PHONE_VERIFICATION_PURPOSE_MISMATCH);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(consumedAt()).isNull();
    }

    @Test
    @DisplayName("인증이 완료되지 않았으면 소비하지 않고 거부한다")
    void issueToken_notVerified_isRejectedWithoutConsumption() {
        saveEmailUser();
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                phoneVerificationRepository.save(
                                        verification(PhoneVerificationPurpose.RESET_PASSWORD)));

        assertErrorCode(ErrorCode.PHONE_VERIFICATION_REQUIRED);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(consumedAt()).isNull();
    }

    @Test
    @DisplayName("인증 결과 유효시간(30분)이 지나면 소비하지 않고 거부한다")
    void issueToken_verifiedResultExpired_isRejectedWithoutConsumption() {
        saveEmailUser();
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            PhoneVerification verification =
                                    verification(PhoneVerificationPurpose.RESET_PASSWORD);
                            verification.verify(now.minusMinutes(31));
                            phoneVerificationRepository.save(verification);
                        });

        assertErrorCode(ErrorCode.PHONE_VERIFICATION_EXPIRED);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(consumedAt()).isNull();
    }

    @Test
    @DisplayName("이미 소비된 인증으로는 다시 발급하지 않는다")
    void issueToken_alreadyConsumedVerification_isRejected() {
        saveEmailUser();
        saveVerifiedVerification(PhoneVerificationPurpose.RESET_PASSWORD);
        issueToken();

        assertErrorCode(ErrorCode.PHONE_VERIFICATION_REQUIRED);

        assertThat(passwordResetTokenRepository.findAll())
                .filteredOn(token -> token.getUser().getId().equals(userId))
                .hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 인증 ID는 404로 거부한다")
    void issueToken_unknownVerification_isRejected() {
        saveEmailUser();

        assertErrorCode(ErrorCode.PHONE_VERIFICATION_NOT_FOUND);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
    }

    private PasswordResetTokenResponse issueToken() {
        return passwordResetService.issueToken(new PasswordResetAuthorityRequest(verificationId));
    }

    private void assertErrorCode(ErrorCode errorCode) {
        assertThatThrownBy(this::issueToken)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private LocalDateTime consumedAt() {
        return phoneVerificationRepository
                .findByVerificationId(verificationId.toString())
                .orElseThrow()
                .getConsumedAt();
    }

    private void deleteVerification(UUID id) {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                phoneVerificationRepository
                                        .findByVerificationId(id.toString())
                                        .ifPresent(phoneVerificationRepository::delete));
    }

    private void saveVerifiedVerification(PhoneVerificationPurpose purpose) {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            PhoneVerification verification = verification(purpose);
                            verification.verify(LocalDateTime.now(clock).minusMinutes(1));
                            phoneVerificationRepository.save(verification);
                        });
    }

    private PhoneVerification verification(PhoneVerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now(clock);
        return PhoneVerification.create(
                verificationId.toString(),
                PHONE_NUMBER,
                purpose,
                "GATHER-RESET0001",
                now.plusMinutes(5),
                now.minusMinutes(1));
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
                                                                "reset-authority@example.com",
                                                                "encoded-password",
                                                                "재설정권한",
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
                                            "legacy-reset-1",
                                            "e".repeat(64),
                                            1,
                                            new EncryptedProviderUserId("ciphertext-reset", 1),
                                            LocalDateTime.now(clock)));
                        });
    }

    private User socialUser() {
        return User.createSocial(
                "카카오회원",
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                PHONE_NUMBER,
                "재설정카카오",
                null,
                true,
                true,
                false,
                null,
                List.of());
    }

    private String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
