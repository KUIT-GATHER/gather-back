package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.config.PhoneVerificationProperties;
import com.gather.gather.domain.auth.dto.PhoneVerificationStartRequest;
import com.gather.gather.domain.auth.dto.PhoneVerificationStatus;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.octomo.client.OctomoApiClient;
import com.gather.gather.domain.auth.octomo.config.OctomoProperties;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 9, 6, 45);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final String VERIFICATION_ID = "5c5d5db1-4187-43d0-8580-672307994878";
    private static final String CODE = "GATHER-7F2K9Q8M4P";
    private static final PhoneVerificationProperties PROPERTIES =
            new PhoneVerificationProperties(
                    Duration.ofSeconds(60), Duration.ofSeconds(3), 30, Duration.ofSeconds(10), 3);

    @Mock private PhoneVerificationRepository phoneVerificationRepository;
    @Mock private PhoneVerificationCodeGenerator codeGenerator;
    @Mock private PhoneVerificationTransactionService transactionService;
    @Mock private OctomoApiClient octomoApiClient;

    private PhoneVerificationService service;

    @BeforeEach
    void setUp() {
        service =
                new PhoneVerificationService(
                        phoneVerificationRepository,
                        new PhoneNumberPolicy(),
                        codeGenerator,
                        transactionService,
                        octomoApiClient,
                        new OctomoProperties(
                                "test-api-key", "https://api.octoverse.kr", "16663538"),
                        PROPERTIES,
                        CLOCK);
    }

    @Test
    @DisplayName("인증 시작은 번호를 정규화하고 UTC 만료 시각을 반환한다")
    void start_createsVerificationSession() {
        when(phoneVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc("01012345678"))
                .thenReturn(Optional.empty());
        when(codeGenerator.generate()).thenReturn(CODE);

        var response = service.start(new PhoneVerificationStartRequest("010-1234-5678"));

        ArgumentCaptor<PhoneVerification> captor = ArgumentCaptor.forClass(PhoneVerification.class);
        verify(phoneVerificationRepository).save(captor.capture());
        PhoneVerification saved = captor.getValue();
        assertThat(saved.getVerificationId()).matches("^[0-9a-f-]{36}$");
        assertThat(saved.getPhoneNumber()).isEqualTo("01012345678");
        assertThat(saved.getVerificationCode()).isEqualTo(CODE);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(response.verificationId()).isEqualTo(saved.getVerificationId());
        assertThat(response.receiverNumber()).isEqualTo("16663538");
        assertThat(response.messageText()).isEqualTo(CODE);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusMinutes(5).toInstant(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("최근 인증 시작 후 60초 이내의 같은 번호 요청은 거부한다")
    void start_rejectsRequestDuringCooldown() {
        PhoneVerification latest =
                PhoneVerification.create(
                        VERIFICATION_ID,
                        "01012345678",
                        CODE,
                        NOW.plusMinutes(4),
                        NOW.minusSeconds(59));
        when(phoneVerificationRepository.findTopByPhoneNumberOrderByCreatedAtDesc("01012345678"))
                .thenReturn(Optional.of(latest));

        assertErrorCode(
                () -> service.start(new PhoneVerificationStartRequest("01012345678")),
                ErrorCode.PHONE_VERIFICATION_RATE_LIMITED);

        verifyNoInteractions(codeGenerator);
    }

    @Test
    @DisplayName("인증 시작은 010으로 시작하는 11자리 휴대폰 번호만 허용한다")
    void start_rejectsInvalidMobileNumber() {
        assertErrorCode(
                () -> service.start(new PhoneVerificationStartRequest("0212345678")),
                ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(phoneVerificationRepository, codeGenerator);
    }

    @Test
    @DisplayName("QR은 내부 예약 성공 후 저장된 인증 문구로 OCTOMO에 요청한다")
    void createQrCode_usesReservedVerificationCode() {
        when(transactionService.reserveQr(VERIFICATION_ID)).thenReturn(CODE);
        when(octomoApiClient.createQrCode(CODE)).thenReturn("data:image/png;base64,cXItY29kZQ==");

        var response = service.createQrCode(VERIFICATION_ID);

        assertThat(response.qrCode()).isEqualTo("data:image/png;base64,cXItY29kZQ==");
        InOrder order = inOrder(transactionService, octomoApiClient);
        order.verify(transactionService).reserveQr(VERIFICATION_ID);
        order.verify(octomoApiClient).createQrCode(CODE);
    }

    @Test
    @DisplayName("이미 VERIFIED인 confirm 예약은 OCTOMO를 다시 조회하지 않는다")
    void confirm_returnsVerifiedWithoutProviderCallWhenAlreadyVerified() {
        when(transactionService.reserveConfirm(VERIFICATION_ID))
                .thenReturn(PhoneVerificationConfirmReservation.verified());

        var response = service.confirm(VERIFICATION_ID);

        assertThat(response.status()).isEqualTo(PhoneVerificationStatus.VERIFIED);
        verifyNoInteractions(octomoApiClient);
        verify(transactionService, never()).verify(VERIFICATION_ID);
    }

    @Test
    @DisplayName("OCTOMO에 문자가 없으면 PENDING이고 최종 상태 변경을 하지 않는다")
    void confirm_returnsPendingWhenMessageDoesNotExist() {
        when(transactionService.reserveConfirm(VERIFICATION_ID))
                .thenReturn(PhoneVerificationConfirmReservation.reserved("01012345678", CODE));
        when(octomoApiClient.existsMessage("01012345678", CODE, 5)).thenReturn(false);

        var response = service.confirm(VERIFICATION_ID);

        assertThat(response.status()).isEqualTo(PhoneVerificationStatus.PENDING);
        verify(transactionService, never()).verify(VERIFICATION_ID);
    }

    @Test
    @DisplayName("OCTOMO 확인 성공 후 별도 잠금 트랜잭션에서 최종 검증한다")
    void confirm_verifiesAfterProviderSuccess() {
        when(transactionService.reserveConfirm(VERIFICATION_ID))
                .thenReturn(PhoneVerificationConfirmReservation.reserved("01012345678", CODE));
        when(octomoApiClient.existsMessage("01012345678", CODE, 5)).thenReturn(true);
        when(transactionService.verify(VERIFICATION_ID))
                .thenReturn(PhoneVerificationStatus.VERIFIED);

        var response = service.confirm(VERIFICATION_ID);

        assertThat(response.status()).isEqualTo(PhoneVerificationStatus.VERIFIED);
        InOrder order = inOrder(transactionService, octomoApiClient);
        order.verify(transactionService).reserveConfirm(VERIFICATION_ID);
        order.verify(octomoApiClient).existsMessage("01012345678", CODE, 5);
        order.verify(transactionService).verify(VERIFICATION_ID);
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
