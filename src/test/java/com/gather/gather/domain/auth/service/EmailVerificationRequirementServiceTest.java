package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
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
class EmailVerificationRequirementServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final UUID VERIFICATION_ID =
            UUID.fromString("98fa88ef-bbeb-4928-a202-7885197b3774");
    private static final UUID OTHER_VERIFICATION_ID =
            UUID.fromString("a0af2979-6032-426d-99fb-bfd972440323");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 10, 0);

    @Mock private EmailVerificationRepository emailVerificationRepository;

    private EmailVerificationRequirementService service;

    @BeforeEach
    void setUp() {
        service =
                new EmailVerificationRequirementService(
                        emailVerificationRepository,
                        Clock.fixed(Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("이메일에 귀속된 유효한 인증 결과를 소비한다")
    void consumeForSignup_consumesValidVerification() {
        EmailVerification verification = verifiedAt(NOW.minusMinutes(29));
        stub(EMAIL, verification);

        service.consumeForSignup(EMAIL, VERIFICATION_ID);

        verify(emailVerificationRepository).delete(verification);
    }

    @Test
    @DisplayName("잠긴 행의 이메일이 요청 이메일과 다르면 거부한다")
    void consumeForSignup_rejectsEmailMismatch() {
        EmailVerification verification =
                verifiedAt("other@example.com", VERIFICATION_ID, NOW.minusMinutes(1));
        stub(EMAIL, verification);

        assertRequired(() -> service.consumeForSignup(EMAIL, VERIFICATION_ID));
        assertThat(verification.getConsumedAt()).isNull();
    }

    @Test
    @DisplayName("잠긴 행의 인증 ID가 요청 proof와 다르면 거부한다")
    void consumeForSignup_rejectsVerificationIdMismatch() {
        EmailVerification verification =
                verifiedAt(EMAIL, OTHER_VERIFICATION_ID, NOW.minusMinutes(1));
        stub(EMAIL, verification);

        assertRequired(() -> service.consumeForSignup(EMAIL, VERIFICATION_ID));
        assertThat(verification.getConsumedAt()).isNull();
    }

    @Test
    @DisplayName("미인증 결과를 거부한다")
    void consumeForSignup_rejectsUnverifiedResult() {
        EmailVerification verification = create();
        stub(EMAIL, verification);

        assertRequired(() -> service.consumeForSignup(EMAIL, VERIFICATION_ID));
    }

    @Test
    @DisplayName("인증 완료 시각이 없는 결과를 거부한다")
    void consumeForSignup_rejectsMissingVerifiedAt() {
        EmailVerification verification = create();
        verification.verify(null);
        stub(EMAIL, verification);

        assertRequired(() -> service.consumeForSignup(EMAIL, VERIFICATION_ID));
    }

    @Test
    @DisplayName("정확히 30분 지난 인증 결과를 거부한다")
    void consumeForSignup_rejectsExpiredResult() {
        EmailVerification verification = verifiedAt(NOW.minusMinutes(30));
        stub(EMAIL, verification);

        assertRequired(() -> service.consumeForSignup(EMAIL, VERIFICATION_ID));
    }

    @Test
    @DisplayName("이미 소비한 인증 결과를 거부한다")
    void consumeForSignup_rejectsConsumedResult() {
        EmailVerification verification = verifiedAt(NOW.minusMinutes(1));
        verification.consume(NOW.minusSeconds(30));
        stub(EMAIL, verification);

        assertRequired(() -> service.consumeForSignup(EMAIL, VERIFICATION_ID));
    }

    @Test
    @DisplayName("누락된 인증 ID는 DB 조회 전에 거부한다")
    void consumeForSignup_rejectsNullVerificationIdWithoutRepositoryCall() {
        assertRequired(() -> service.consumeForSignup(EMAIL, null));

        verifyNoInteractions(emailVerificationRepository);
    }

    @Test
    @DisplayName("이메일 인증 행이 없으면 locking lookup 없이 거부한다")
    void consumeForSignup_rejectsMissingEmailRowWithoutLockingLookup() {
        when(emailVerificationRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertRequired(() -> service.consumeForSignup(EMAIL, VERIFICATION_ID));

        verify(emailVerificationRepository, never()).findByEmailForUpdate(EMAIL);
    }

    @Test
    @DisplayName("선조회 후 인증 행이 사라지면 locking lookup 결과로 거부한다")
    void consumeForSignup_rejectsMissingLockedRow() {
        when(emailVerificationRepository.existsByEmail(EMAIL)).thenReturn(true);
        when(emailVerificationRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.empty());

        assertRequired(() -> service.consumeForSignup(EMAIL, VERIFICATION_ID));
    }

    private EmailVerification create() {
        return create(EMAIL, VERIFICATION_ID);
    }

    private EmailVerification create(String email, UUID verificationId) {
        return EmailVerification.create(
                email,
                verificationId.toString(),
                "123456",
                NOW.plusMinutes(10),
                NOW.minusMinutes(5));
    }

    private EmailVerification verifiedAt(LocalDateTime verifiedAt) {
        EmailVerification verification = create();
        verification.verify(verifiedAt);
        return verification;
    }

    private EmailVerification verifiedAt(
            String email, UUID verificationId, LocalDateTime verifiedAt) {
        EmailVerification verification = create(email, verificationId);
        verification.verify(verifiedAt);
        return verification;
    }

    private void stub(String email, EmailVerification verification) {
        when(emailVerificationRepository.existsByEmail(email)).thenReturn(true);
        when(emailVerificationRepository.findByEmailForUpdate(email))
                .thenReturn(Optional.of(verification));
    }

    private void assertRequired(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_VERIFICATION_REQUIRED));
    }
}
