package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        stub(verification);

        service.consumeForSignup(VERIFICATION_ID, EMAIL);

        assertThat(verification.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이메일이 다른 인증 결과를 거부한다")
    void consumeForSignup_rejectsEmailMismatch() {
        EmailVerification verification = verifiedAt(NOW.minusMinutes(1));
        stub(verification);

        assertRequired(() -> service.consumeForSignup(VERIFICATION_ID, "other@example.com"));
    }

    @Test
    @DisplayName("미인증 결과를 거부한다")
    void consumeForSignup_rejectsUnverifiedResult() {
        EmailVerification verification = create();
        stub(verification);

        assertRequired(() -> service.consumeForSignup(VERIFICATION_ID, EMAIL));
    }

    @Test
    @DisplayName("정확히 30분 지난 인증 결과를 거부한다")
    void consumeForSignup_rejectsExpiredResult() {
        EmailVerification verification = verifiedAt(NOW.minusMinutes(30));
        stub(verification);

        assertRequired(() -> service.consumeForSignup(VERIFICATION_ID, EMAIL));
    }

    @Test
    @DisplayName("이미 소비한 인증 결과를 거부한다")
    void consumeForSignup_rejectsConsumedResult() {
        EmailVerification verification = verifiedAt(NOW.minusMinutes(1));
        verification.consume(NOW.minusSeconds(30));
        stub(verification);

        assertRequired(() -> service.consumeForSignup(VERIFICATION_ID, EMAIL));
    }

    @Test
    @DisplayName("누락되거나 존재하지 않는 인증 ID를 같은 오류로 거부한다")
    void consumeForSignup_rejectsMissingResult() {
        assertRequired(() -> service.consumeForSignup(null, EMAIL));
        when(emailVerificationRepository.findByVerificationIdForUpdate(VERIFICATION_ID.toString()))
                .thenReturn(Optional.empty());
        assertRequired(() -> service.consumeForSignup(VERIFICATION_ID, EMAIL));
    }

    private EmailVerification create() {
        return EmailVerification.create(
                EMAIL,
                VERIFICATION_ID.toString(),
                "123456",
                NOW.plusMinutes(10),
                NOW.minusMinutes(5));
    }

    private EmailVerification verifiedAt(LocalDateTime verifiedAt) {
        EmailVerification verification = create();
        verification.verify(verifiedAt);
        return verification;
    }

    private void stub(EmailVerification verification) {
        when(emailVerificationRepository.findByVerificationIdForUpdate(VERIFICATION_ID.toString()))
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
