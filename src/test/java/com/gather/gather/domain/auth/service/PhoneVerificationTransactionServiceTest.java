package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.config.PhoneVerificationProperties;
import com.gather.gather.domain.auth.dto.PhoneVerificationStatus;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationTransactionServiceTest {

    private static final String VERIFICATION_ID = "5c5d5db1-4187-43d0-8580-672307994878";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 6, 45);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final PhoneVerificationProperties PROPERTIES =
            new PhoneVerificationProperties(
                    Duration.ofSeconds(60), Duration.ofSeconds(3), 30, Duration.ofSeconds(10), 3);

    @Mock private PhoneVerificationRepository phoneVerificationRepository;
    @Mock private SignupValidator signupValidator;
    @Mock private AccountRejoinBlockService accountRejoinBlockService;

    private PhoneVerificationTransactionService service;

    @BeforeEach
    void setUp() {
        service =
                new PhoneVerificationTransactionService(
                        phoneVerificationRepository,
                        signupValidator,
                        accountRejoinBlockService,
                        PROPERTIES,
                        CLOCK);
    }

    @Test
    @DisplayName("confirm 예약은 잠금 안에서 시도 횟수와 시각을 기록한다")
    void reserveConfirm_recordsAttempt() {
        PhoneVerification verification = activeVerification();
        stubVerification(verification);

        PhoneVerificationConfirmReservation reservation = service.reserveConfirm(VERIFICATION_ID);

        assertThat(reservation.alreadyVerified()).isFalse();
        assertThat(reservation.phoneNumber()).isEqualTo("01012345678");
        assertThat(reservation.verificationCode()).isEqualTo("GATHER-7F2K9Q8M4P");
        assertThat(verification.getConfirmAttemptCount()).isEqualTo(1);
        assertThat(verification.getLastConfirmAttemptAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("존재하지 않는 인증 세션은 잠금 조회 결과로 판별한다")
    void reserveConfirm_rejectsMissingVerificationFromLockQuery() {
        assertErrorCode(
                () -> service.reserveConfirm(VERIFICATION_ID),
                ErrorCode.PHONE_VERIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("confirm 재시도 쿨다운 중에는 공급자를 호출할 예약을 거부한다")
    void reserveConfirm_rejectsDuringCooldown() {
        PhoneVerification verification = activeVerification();
        verification.reserveConfirm(NOW.minusSeconds(2));
        stubVerification(verification);

        assertErrorCode(
                () -> service.reserveConfirm(VERIFICATION_ID),
                ErrorCode.PHONE_VERIFICATION_RATE_LIMITED);
    }

    @Test
    @DisplayName("이미 완료된 유효한 인증은 외부 조회 없이 VERIFIED로 예약한다")
    void reserveConfirm_returnsAlreadyVerified() {
        PhoneVerification verification = activeVerification();
        verification.verify(NOW.minusMinutes(1));
        stubVerification(verification);

        PhoneVerificationConfirmReservation reservation = service.reserveConfirm(VERIFICATION_ID);

        assertThat(reservation.alreadyVerified()).isTrue();
        assertThat(verification.getConfirmAttemptCount()).isZero();
    }

    @Test
    @DisplayName("QR 예약은 잠금 안에서 요청 횟수와 시각을 기록한다")
    void reserveQr_recordsRequest() {
        PhoneVerification verification = activeVerification();
        stubVerification(verification);

        assertThat(service.reserveQr(VERIFICATION_ID)).isEqualTo("GATHER-7F2K9Q8M4P");
        assertThat(verification.getQrRequestCount()).isEqualTo(1);
        assertThat(verification.getLastQrRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("최종 검증은 잠금 후 만료를 다시 확인하고 VERIFIED로 변경한다")
    void verify_marksSessionVerified() {
        PhoneVerification verification = activeVerification();
        stubVerification(verification);
        when(accountRejoinBlockService.isPhoneBlocked("01012345678", NOW)).thenReturn(false);

        PhoneVerificationStatus result = service.verify(VERIFICATION_ID);

        assertThat(result).isEqualTo(PhoneVerificationStatus.VERIFIED);
        assertThat(verification.isVerified()).isTrue();
        assertThat(verification.getVerifiedAt()).isEqualTo(NOW);
        verify(signupValidator).validatePhoneNumberNotDuplicated("01012345678");
    }

    @Test
    @DisplayName("최종 잠금 시점에 만료된 인증은 상태를 변경하지 않는다")
    void verify_rechecksExpirationAfterLock() {
        PhoneVerification verification =
                PhoneVerification.create(
                        VERIFICATION_ID,
                        "01012345678",
                        "GATHER-7F2K9Q8M4P",
                        NOW,
                        NOW.minusMinutes(5));
        stubVerification(verification);

        assertErrorCode(
                () -> service.verify(VERIFICATION_ID), ErrorCode.PHONE_VERIFICATION_EXPIRED);

        assertThat(verification.isVerified()).isFalse();
        verify(signupValidator, never()).validatePhoneNumberNotDuplicated("01012345678");
    }

    @Test
    @DisplayName("중복 번호는 최종 VERIFIED 상태로 변경하지 않는다")
    void verify_rejectsDuplicatePhoneWithoutStateChange() {
        PhoneVerification verification = activeVerification();
        stubVerification(verification);
        doThrow(new BusinessException(ErrorCode.DUPLICATE_PHONE_NUMBER))
                .when(signupValidator)
                .validatePhoneNumberNotDuplicated("01012345678");

        assertErrorCode(() -> service.verify(VERIFICATION_ID), ErrorCode.DUPLICATE_PHONE_NUMBER);

        assertThat(verification.isVerified()).isFalse();
    }

    @Test
    @DisplayName("재가입 제한 번호는 최종 VERIFIED 상태로 변경하지 않는다")
    void verify_rejectsRejoinBlockedPhoneWithoutStateChange() {
        PhoneVerification verification = activeVerification();
        stubVerification(verification);
        when(accountRejoinBlockService.isPhoneBlocked("01012345678", NOW)).thenReturn(true);

        assertErrorCode(() -> service.verify(VERIFICATION_ID), ErrorCode.ACCOUNT_REJOIN_BLOCKED);

        assertThat(verification.isVerified()).isFalse();
    }

    private PhoneVerification activeVerification() {
        return PhoneVerification.create(
                VERIFICATION_ID,
                "01012345678",
                "GATHER-7F2K9Q8M4P",
                NOW.plusMinutes(5),
                NOW.minusMinutes(1));
    }

    private void stubVerification(PhoneVerification verification) {
        when(phoneVerificationRepository.findByVerificationIdForUpdate(VERIFICATION_ID))
                .thenReturn(Optional.of(verification));
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
