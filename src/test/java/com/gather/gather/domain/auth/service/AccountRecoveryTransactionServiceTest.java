package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryTransactionServiceTest {

    private static final UUID VERIFICATION_ID =
            UUID.fromString("5c5d5db1-4187-43d0-8580-672307994878");
    private static final String PHONE_NUMBER = "01012345678";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 6, 45);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock private PhoneVerificationRepository phoneVerificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccountLoginTypeResolver accountLoginTypeResolver;
    @Mock private User user;

    private AccountRecoveryTransactionService service;

    @BeforeEach
    void setUp() {
        service =
                new AccountRecoveryTransactionService(
                        phoneVerificationRepository,
                        userRepository,
                        accountLoginTypeResolver,
                        CLOCK);
    }

    @Test
    @DisplayName("EMAIL 계정 결과를 반환하고 인증을 소비한다")
    void recoverEmail_returnsEmailAndConsumesVerification() {
        PhoneVerification verification = verifiedFindAccount();
        stubVerification(verification);
        when(userRepository.findByPhoneNumberForUpdate(PHONE_NUMBER)).thenReturn(Optional.of(user));
        when(accountLoginTypeResolver.resolve(user))
                .thenReturn(Optional.of(AccountLoginType.EMAIL));
        when(user.getEmail()).thenReturn("user@example.com");

        AccountRecoveryTransactionResult result = service.recoverEmail(VERIFICATION_ID);

        assertThat(result.outcome()).isEqualTo(AccountRecoveryTransactionResult.Outcome.EMAIL);
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(verification.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("KAKAO 계정 결과를 반환하고 인증을 소비한다")
    void recoverEmail_returnsKakaoAndConsumesVerification() {
        PhoneVerification verification = verifiedFindAccount();
        stubVerification(verification);
        when(userRepository.findByPhoneNumberForUpdate(PHONE_NUMBER)).thenReturn(Optional.of(user));
        when(accountLoginTypeResolver.resolve(user))
                .thenReturn(Optional.of(AccountLoginType.KAKAO));

        AccountRecoveryTransactionResult result = service.recoverEmail(VERIFICATION_ID);

        assertThat(result.outcome()).isEqualTo(AccountRecoveryTransactionResult.Outcome.KAKAO);
        assertThat(result.email()).isNull();
        assertThat(verification.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("계정이 없어도 ACCOUNT_NOT_FOUND 결과를 반환하고 인증을 소비한다")
    void recoverEmail_consumesVerificationWhenAccountDoesNotExist() {
        PhoneVerification verification = verifiedFindAccount();
        stubVerification(verification);
        when(userRepository.findByPhoneNumberForUpdate(PHONE_NUMBER)).thenReturn(Optional.empty());

        AccountRecoveryTransactionResult result = service.recoverEmail(VERIFICATION_ID);

        assertThat(result.outcome())
                .isEqualTo(AccountRecoveryTransactionResult.Outcome.ACCOUNT_NOT_FOUND);
        assertThat(verification.getConsumedAt()).isEqualTo(NOW);
        verifyNoInteractions(accountLoginTypeResolver);
    }

    @Test
    @DisplayName("계정 유형을 안전하게 판정할 수 없어도 인증을 소비한다")
    void recoverEmail_consumesVerificationForUnrecoverableAccount() {
        PhoneVerification verification = verifiedFindAccount();
        stubVerification(verification);
        when(userRepository.findByPhoneNumberForUpdate(PHONE_NUMBER)).thenReturn(Optional.of(user));
        when(accountLoginTypeResolver.resolve(user)).thenReturn(Optional.empty());

        AccountRecoveryTransactionResult result = service.recoverEmail(VERIFICATION_ID);

        assertThat(result.outcome())
                .isEqualTo(AccountRecoveryTransactionResult.Outcome.ACCOUNT_NOT_FOUND);
        assertThat(verification.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("FIND_ACCOUNT가 아닌 인증은 목적 불일치로 거부하고 소비하지 않는다")
    void recoverEmail_rejectsPurposeMismatch() {
        PhoneVerification verification =
                verifiedVerification(PhoneVerificationPurpose.SIGNUP, NOW.minusMinutes(1));
        stubVerification(verification);

        assertErrorCode(
                () -> service.recoverEmail(VERIFICATION_ID),
                ErrorCode.PHONE_VERIFICATION_PURPOSE_MISMATCH);

        assertThat(verification.isConsumed()).isFalse();
        verifyNoInteractions(userRepository, accountLoginTypeResolver);
    }

    @Test
    @DisplayName("인증 완료 후 30분이 지나면 만료 오류로 거부한다")
    void recoverEmail_rejectsExpiredVerifiedResult() {
        PhoneVerification verification =
                verifiedVerification(PhoneVerificationPurpose.FIND_ACCOUNT, NOW.minusMinutes(30));
        stubVerification(verification);

        assertErrorCode(
                () -> service.recoverEmail(VERIFICATION_ID), ErrorCode.PHONE_VERIFICATION_EXPIRED);

        assertThat(verification.isConsumed()).isFalse();
    }

    @Test
    @DisplayName("User 조회 시스템 오류가 발생하면 인증을 소비하지 않는다")
    void recoverEmail_doesNotConsumeOnUnexpectedFailure() {
        PhoneVerification verification = verifiedFindAccount();
        stubVerification(verification);
        when(userRepository.findByPhoneNumberForUpdate(PHONE_NUMBER))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.recoverEmail(VERIFICATION_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(verification.isConsumed()).isFalse();
    }

    private PhoneVerification verifiedFindAccount() {
        return verifiedVerification(PhoneVerificationPurpose.FIND_ACCOUNT, NOW.minusMinutes(1));
    }

    private PhoneVerification verifiedVerification(
            PhoneVerificationPurpose purpose, LocalDateTime verifiedAt) {
        PhoneVerification verification =
                PhoneVerification.create(
                        VERIFICATION_ID.toString(),
                        PHONE_NUMBER,
                        purpose,
                        "GATHER-RECOVERY01",
                        verifiedAt.plusMinutes(5),
                        verifiedAt.minusMinutes(1));
        verification.verify(verifiedAt);
        return verification;
    }

    private void stubVerification(PhoneVerification verification) {
        when(phoneVerificationRepository.findByVerificationIdForUpdate(VERIFICATION_ID.toString()))
                .thenReturn(Optional.of(verification));
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
