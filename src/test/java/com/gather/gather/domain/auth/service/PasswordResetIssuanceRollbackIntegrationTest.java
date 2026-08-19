package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.dto.PasswordResetAuthorityRequest;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 발급 트랜잭션이 실패하면 휴대폰 인증 소비까지 함께 롤백되는지 검증한다. */
@SpringBootTest
class PasswordResetIssuanceRollbackIntegrationTest {

    private static final String PHONE_NUMBER = "01096660102";
    private static final String CONFLICT_PHONE_NUMBER = "01096660103";
    private static final String COLLIDING_TOKEN = "A".repeat(43);
    private static final String RETRIED_TOKEN = "B".repeat(43);
    private static final String COLLIDING_HASH = "a".repeat(64);
    private static final String RETRIED_HASH = "b".repeat(64);

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    @MockitoBean private PasswordResetTokenCodec passwordResetTokenCodec;
    @MockitoBean private AccountLoginTypeResolver accountLoginTypeResolver;

    private UUID verificationId;
    private Long userId;
    private Long conflictUserId;

    @BeforeEach
    void setUp() {
        verificationId = UUID.randomUUID();
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            userId =
                                    userRepository
                                            .save(
                                                    emailUser(
                                                            PHONE_NUMBER,
                                                            "reset-rollback@example.com",
                                                            "롤백재설정"))
                                            .getId();
                            LocalDateTime now = LocalDateTime.now(clock);
                            PhoneVerification verification =
                                    PhoneVerification.create(
                                            verificationId.toString(),
                                            PHONE_NUMBER,
                                            PhoneVerificationPurpose.RESET_PASSWORD,
                                            "GATHER-RESET0002",
                                            now.plusMinutes(5),
                                            now.minusMinutes(1));
                            verification.verify(now.minusMinutes(1));
                            phoneVerificationRepository.save(verification);
                        });
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            passwordResetTokenRepository.deleteAllByUserId(userId);
                            if (conflictUserId != null) {
                                passwordResetTokenRepository.deleteAllByUserId(conflictUserId);
                            }
                            phoneVerificationRepository
                                    .findByVerificationId(verificationId.toString())
                                    .ifPresent(phoneVerificationRepository::delete);
                            userRepository.deleteById(userId);
                            if (conflictUserId != null) {
                                userRepository.deleteById(conflictUserId);
                            }
                        });
        conflictUserId = null;
    }

    @Test
    @DisplayName("계정 판정 중 시스템 오류가 나면 토큰을 만들지 않고 인증 소비도 롤백한다")
    void issueToken_rollsBackConsumptionOnUnexpectedFailure() {
        when(passwordResetTokenCodec.generateToken()).thenReturn(COLLIDING_TOKEN);
        when(accountLoginTypeResolver.resolveForActiveAccount(any(User.class)))
                .thenThrow(new IllegalStateException("classification failure"));

        assertThatThrownBy(
                        () ->
                                passwordResetService.issueToken(
                                        new PasswordResetAuthorityRequest(verificationId)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(consumedAt()).isNull();
    }

    @Test
    @DisplayName("token hash UNIQUE 충돌은 새 토큰으로 발급 트랜잭션 전체를 다시 시도한다")
    void issueToken_retriesWithNewTokenAfterHashConflict() {
        saveConflictingToken();
        when(passwordResetTokenCodec.generateToken()).thenReturn(COLLIDING_TOKEN, RETRIED_TOKEN);
        when(passwordResetTokenCodec.validateAndHash(COLLIDING_TOKEN)).thenReturn(COLLIDING_HASH);
        when(passwordResetTokenCodec.validateAndHash(RETRIED_TOKEN)).thenReturn(RETRIED_HASH);
        when(accountLoginTypeResolver.resolveForActiveAccount(any(User.class)))
                .thenReturn(Optional.of(AccountLoginType.EMAIL));

        String issued =
                passwordResetService
                        .issueToken(new PasswordResetAuthorityRequest(verificationId))
                        .passwordResetToken();

        assertThat(issued).isEqualTo(RETRIED_TOKEN);
        assertThat(passwordResetTokenRepository.findByUserId(userId))
                .get()
                .extracting(PasswordResetToken::getTokenHash)
                .isEqualTo(RETRIED_HASH);
        assertThat(consumedAt()).isNotNull();
        verify(passwordResetTokenCodec, times(2)).generateToken();
    }

    @Test
    @DisplayName("재시도 3회가 모두 충돌하면 시스템 실패로 끝나고 인증은 소비되지 않는다")
    void issueToken_exhaustsRetriesAndRollsBackConsumption() {
        saveConflictingToken();
        when(passwordResetTokenCodec.generateToken()).thenReturn(COLLIDING_TOKEN);
        when(passwordResetTokenCodec.validateAndHash(COLLIDING_TOKEN)).thenReturn(COLLIDING_HASH);
        when(accountLoginTypeResolver.resolveForActiveAccount(any(User.class)))
                .thenReturn(Optional.of(AccountLoginType.EMAIL));

        assertThatThrownBy(
                        () ->
                                passwordResetService.issueToken(
                                        new PasswordResetAuthorityRequest(verificationId)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(consumedAt()).isNull();
        verify(passwordResetTokenCodec, times(3)).generateToken();
    }

    private void saveConflictingToken() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            User conflictUser =
                                    userRepository.save(
                                            emailUser(
                                                    CONFLICT_PHONE_NUMBER,
                                                    "reset-conflict@example.com",
                                                    "충돌재설정"));
                            conflictUserId = conflictUser.getId();
                            LocalDateTime now = LocalDateTime.now(clock);
                            passwordResetTokenRepository.save(
                                    PasswordResetToken.issue(
                                            conflictUser,
                                            COLLIDING_HASH,
                                            now.plusMinutes(10),
                                            now));
                        });
    }

    private LocalDateTime consumedAt() {
        return phoneVerificationRepository
                .findByVerificationId(verificationId.toString())
                .orElseThrow()
                .getConsumedAt();
    }

    private User emailUser(String phoneNumber, String email, String nickname) {
        return User.create(
                "재설정회원",
                LocalDate.of(2000, 1, 1),
                Gender.FEMALE,
                phoneNumber,
                email,
                "encoded-password",
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
