package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
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
class PhoneVerificationRequirementServiceTest {

    private static final UUID VERIFICATION_ID =
            UUID.fromString("5c5d5db1-4187-43d0-8580-672307994878");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 6, 45);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Mock private PhoneVerificationRepository phoneVerificationRepository;

    private PhoneVerificationRequirementService service;

    @BeforeEach
    void setUp() {
        service = new PhoneVerificationRequirementService(phoneVerificationRepository, CLOCK);
    }

    @Test
    @DisplayName("검증 ID와 전화번호가 일치하는 유효한 인증 결과를 소비한다")
    void consumeForSignup_consumesValidVerification() {
        PhoneVerification verification = verifiedAt(NOW.minusMinutes(29));
        stubVerification(verification);

        assertThatCode(() -> service.consumeForSignup(VERIFICATION_ID, "01012345678"))
                .doesNotThrowAnyException();

        assertThat(verification.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("같은 인증 결과를 두 번 소비할 수 없다")
    void consumeForSignup_rejectsConsumedVerification() {
        PhoneVerification verification = verifiedAt(NOW.minusMinutes(1));
        verification.consume(NOW.minusSeconds(1));
        stubVerification(verification);

        assertRequired(() -> service.consumeForSignup(VERIFICATION_ID, "01012345678"));
    }

    @Test
    @DisplayName("인증한 전화번호와 가입 전화번호가 다르면 거부한다")
    void consumeForSignup_rejectsPhoneMismatch() {
        stubVerification(verifiedAt(NOW.minusMinutes(1)));

        assertRequired(() -> service.consumeForSignup(VERIFICATION_ID, "01099999999"));
    }

    @Test
    @DisplayName("인증 완료 후 정확히 30분이 지나면 거부한다")
    void consumeForSignup_rejectsExpiredVerification() {
        stubVerification(verifiedAt(NOW.minusMinutes(30)));

        assertRequired(() -> service.consumeForSignup(VERIFICATION_ID, "01012345678"));
    }

    @Test
    @DisplayName("존재하지 않는 검증 ID는 동일한 인증 필요 오류로 거부한다")
    void consumeForSignup_rejectsMissingVerification() {
        assertRequired(() -> service.consumeForSignup(VERIFICATION_ID, "01012345678"));
    }

    @Test
    @DisplayName("회원가입은 다른 목적의 인증 결과를 소비할 수 없다")
    void consumeForSignup_rejectsPurposeMismatch() {
        PhoneVerification verification =
                verifiedAt(NOW.minusMinutes(1), PhoneVerificationPurpose.FIND_ACCOUNT);
        stubVerification(verification);

        assertThatThrownBy(() -> service.consumeForSignup(VERIFICATION_ID, "01012345678"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PHONE_VERIFICATION_PURPOSE_MISMATCH));
    }

    private PhoneVerification verifiedAt(LocalDateTime verifiedAt) {
        return verifiedAt(verifiedAt, PhoneVerificationPurpose.SIGNUP);
    }

    private PhoneVerification verifiedAt(
            LocalDateTime verifiedAt, PhoneVerificationPurpose purpose) {
        PhoneVerification verification =
                PhoneVerification.create(
                        VERIFICATION_ID.toString(),
                        "01012345678",
                        purpose,
                        "GATHER-7F2K9Q8M4P",
                        verifiedAt.plusMinutes(5),
                        verifiedAt.minusMinutes(1));
        verification.verify(verifiedAt);
        return verification;
    }

    private void stubVerification(PhoneVerification verification) {
        when(phoneVerificationRepository.findByVerificationIdForUpdate(VERIFICATION_ID.toString()))
                .thenReturn(Optional.of(verification));
    }

    private void assertRequired(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PHONE_VERIFICATION_REQUIRED));
    }
}
