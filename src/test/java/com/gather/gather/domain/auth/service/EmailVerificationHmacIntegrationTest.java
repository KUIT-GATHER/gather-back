package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.auth.dto.EmailVerificationConfirmRequest;
import com.gather.gather.domain.auth.dto.EmailVerificationConfirmResponse;
import com.gather.gather.domain.auth.dto.EmailVerificationSendRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 인증 코드가 평문 대신 HMAC으로만 저장되고, 구 버전 JAR이 남긴 평문 행이 요청 경로에서 차단되는지 검증한다.
 *
 * <p>평문 행은 현재 엔티티로 만들 수 없어 실제 DB에 직접 INSERT해야 하므로 통합 테스트가 필요하다.
 */
@SpringBootTest
class EmailVerificationHmacIntegrationTest {

    private static final String EMAIL = "hmac-verification-test@example.com";
    private static final String LEGACY_CODE = "123456";
    private static final int RESEND_COOLDOWN_MINUTES = 3;

    @Autowired private AuthService authService;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private EmailVerificationCodeHasher emailVerificationCodeHasher;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmailSender emailSender;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbcTemplate.update("DELETE FROM email_verification WHERE email = ?", EMAIL);
    }

    @Test
    @DisplayName("발송하면 평문 코드는 메일로만 나가고 DB에는 HMAC만 남는다")
    void send_persistsOnlyHmacAndDeliversRawCode() {
        String rawCode = sendAndCaptureCode();

        EmailVerification stored = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getCode()).isEmpty();
        assertThat(stored.getCodeHash())
                .isEqualTo(emailVerificationCodeHasher.hash(stored.getVerificationId(), rawCode));
        assertThat(stored.getCodeHash()).matches("[0-9a-f]{64}");
        assertThat(stored.isLegacyFormat()).isFalse();
        assertThat(rowsHoldingValue(rawCode)).isZero();
    }

    @Test
    @DisplayName("발송해도 기존 만료·발송 횟수 정책은 그대로 유지된다")
    void send_keepsExistingLifecycleFields() {
        sendAndCaptureCode();

        EmailVerification stored = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getExpiresAt()).isEqualTo(stored.getCreatedAt().plusMinutes(10));
        assertThat(stored.getDailySendCount()).isEqualTo(1);
        assertThat(stored.getAttemptCount()).isZero();
        assertThat(stored.isVerified()).isFalse();
    }

    @Test
    @DisplayName("발송된 코드는 HMAC 검증을 통과해 인증에 성공한다")
    void confirm_correctCode_succeeds() {
        String rawCode = sendAndCaptureCode();

        EmailVerificationConfirmResponse response =
                authService.confirmEmailVerificationCode(
                        new EmailVerificationConfirmRequest(EMAIL, rawCode));

        EmailVerification stored = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.isVerified()).isTrue();
        assertThat(response.emailVerificationId())
                .isEqualTo(UUID.fromString(stored.getVerificationId()));
    }

    @Test
    @DisplayName("틀린 코드는 시도 횟수를 늘리고 인증에 실패한다")
    void confirm_wrongCode_increasesAttempt() {
        String rawCode = sendAndCaptureCode();
        String wrongCode = rawCode.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(
                        () ->
                                authService.confirmEmailVerificationCode(
                                        new EmailVerificationConfirmRequest(EMAIL, wrongCode)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_VERIFICATION_CODE));

        EmailVerification stored = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        assertThat(stored.isVerified()).isFalse();
    }

    @Test
    @DisplayName("다른 인증 ID로 만든 해시는 재사용할 수 없다")
    void confirm_codeHashBoundToOtherVerificationId_fails() {
        String rawCode = sendAndCaptureCode();
        EmailVerification stored = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        ReflectionTestUtils.setField(
                stored,
                "codeHash",
                emailVerificationCodeHasher.hash(UUID.randomUUID().toString(), rawCode));
        emailVerificationRepository.saveAndFlush(stored);

        assertThatThrownBy(
                        () ->
                                authService.confirmEmailVerificationCode(
                                        new EmailVerificationConfirmRequest(EMAIL, rawCode)))
                .isInstanceOf(BusinessException.class);
        assertThat(emailVerificationRepository.findByEmail(EMAIL).orElseThrow().isVerified())
                .isFalse();
    }

    @Test
    @DisplayName("구 버전이 남긴 평문 행은 시도 횟수를 소모하지 않고 인증 요청 없음으로 막는다")
    void confirm_legacyPlaintextRow_failsClosed() {
        insertLegacyRow(LEGACY_CODE, null);

        assertThatThrownBy(
                        () ->
                                authService.confirmEmailVerificationCode(
                                        new EmailVerificationConfirmRequest(EMAIL, LEGACY_CODE)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));

        EmailVerification stored = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.isVerified()).isFalse();
        assertThat(stored.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("구 버전이 갱신해 해시가 낡은 행도 인증에 사용하지 않는다")
    void confirm_staleCodeHashWithPlaintextCode_failsClosed() {
        String verificationId = UUID.randomUUID().toString();
        insertLegacyRow(
                verificationId,
                LEGACY_CODE,
                emailVerificationCodeHasher.hash(verificationId, LEGACY_CODE));

        assertThatThrownBy(
                        () ->
                                authService.confirmEmailVerificationCode(
                                        new EmailVerificationConfirmRequest(EMAIL, LEGACY_CODE)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
        assertThat(emailVerificationRepository.findByEmail(EMAIL).orElseThrow().isVerified())
                .isFalse();
    }

    @Test
    @DisplayName("해시 형식이 깨진 행은 어떤 코드로도 인증되지 않는다")
    void confirm_malformedCodeHash_failsClosed() {
        String rawCode = sendAndCaptureCode();
        EmailVerification stored = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        ReflectionTestUtils.setField(stored, "codeHash", "not-a-valid-hash");
        emailVerificationRepository.saveAndFlush(stored);

        assertThatThrownBy(
                        () ->
                                authService.confirmEmailVerificationCode(
                                        new EmailVerificationConfirmRequest(EMAIL, rawCode)))
                .isInstanceOf(BusinessException.class);
        assertThat(emailVerificationRepository.findByEmail(EMAIL).orElseThrow().isVerified())
                .isFalse();
    }

    @Test
    @DisplayName("재발송하면 인증 ID와 해시가 함께 회전해 이전 코드는 통하지 않는다")
    void resend_rotatesVerificationIdAndCodeHash() {
        String firstCode = sendAndCaptureCode();
        EmailVerification first = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        String firstVerificationId = first.getVerificationId();
        String firstCodeHash = first.getCodeHash();
        expireResendCooldown(first);

        String secondCode = sendAndCaptureCode(2);

        EmailVerification second = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(second.getVerificationId()).isNotEqualTo(firstVerificationId);
        assertThat(second.getCodeHash()).isNotEqualTo(firstCodeHash);
        assertThat(second.getDailySendCount()).isEqualTo(2);
        assertThat(second.getCode()).isEmpty();

        // 코드는 6자리 난수라 드물게 같은 값이 다시 나올 수 있어, 값이 다를 때만 무효화를 확인한다.
        if (!firstCode.equals(secondCode)) {
            assertThatThrownBy(
                            () ->
                                    authService.confirmEmailVerificationCode(
                                            new EmailVerificationConfirmRequest(EMAIL, firstCode)))
                    .isInstanceOf(BusinessException.class);
        }
        authService.confirmEmailVerificationCode(
                new EmailVerificationConfirmRequest(EMAIL, secondCode));
        assertThat(emailVerificationRepository.findByEmail(EMAIL).orElseThrow().isVerified())
                .isTrue();
    }

    @Test
    @DisplayName("재발송 메일이 실패하면 이전 인증 ID와 해시가 함께 복구되어 이전 코드로 인증할 수 있다")
    void resend_smtpFailure_restoresPreviousCodeSoItStillConfirms() {
        String firstCode = sendAndCaptureCode();
        EmailVerification first = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        String firstVerificationId = first.getVerificationId();
        String firstCodeHash = first.getCodeHash();
        expireResendCooldown(first);

        doThrow(new RuntimeException("smtp down"))
                .when(emailSender)
                .sendVerificationCode(anyString(), anyString());

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(EMAIL)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_SEND_FAILED));

        EmailVerification restored = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(restored.getVerificationId()).isEqualTo(firstVerificationId);
        assertThat(restored.getCodeHash()).isEqualTo(firstCodeHash);

        authService.confirmEmailVerificationCode(
                new EmailVerificationConfirmRequest(EMAIL, firstCode));
        assertThat(emailVerificationRepository.findByEmail(EMAIL).orElseThrow().isVerified())
                .isTrue();
    }

    @Test
    @DisplayName("평문 행에서 시작한 재발송이 실패하면 평문 상태를 되살리지 않고 행을 삭제한다")
    void resend_fromLegacyRow_smtpFailure_deletesRowInsteadOfRestoringPlaintext() {
        insertLegacyRow(LEGACY_CODE, null);

        doThrow(new RuntimeException("smtp down"))
                .when(emailSender)
                .sendVerificationCode(anyString(), anyString());

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(EMAIL)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_SEND_FAILED));

        assertThat(emailVerificationRepository.findByEmail(EMAIL)).isEmpty();
    }

    private String sendAndCaptureCode() {
        return sendAndCaptureCode(1);
    }

    private String sendAndCaptureCode(int expectedSendCount) {
        authService.sendEmailVerificationCode(new EmailVerificationSendRequest(EMAIL));
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(expectedSendCount))
                .sendVerificationCode(eq(EMAIL), codeCaptor.capture());
        return codeCaptor.getValue();
    }

    private void expireResendCooldown(EmailVerification verification) {
        ReflectionTestUtils.setField(
                verification,
                "createdAt",
                LocalDateTime.now().minusMinutes(RESEND_COOLDOWN_MINUTES + 1));
        emailVerificationRepository.saveAndFlush(verification);
    }

    private void insertLegacyRow(String code, String codeHash) {
        insertLegacyRow(UUID.randomUUID().toString(), code, codeHash);
    }

    /** 구 버전 JAR이 만든 평문 행은 현재 엔티티로 만들 수 없어 직접 INSERT한다. */
    private void insertLegacyRow(String verificationId, String code, String codeHash) {
        jdbcTemplate.update(
                """
                INSERT INTO email_verification
                    (version, email, verification_id, code, code_hash, verified,
                     expires_at, created_at, daily_send_count, attempt_count)
                VALUES (0, ?, ?, ?, ?, 0, ?, ?, 1, 0)
                """,
                EMAIL,
                verificationId,
                code,
                codeHash,
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().minusMinutes(RESEND_COOLDOWN_MINUTES + 1));
    }

    private int rowsHoldingValue(String value) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM email_verification
                        WHERE email = ? AND (code = ? OR code_hash = ? OR verification_id = ?)
                        """,
                        Integer.class,
                        EMAIL,
                        value,
                        value,
                        value);
        return count == null ? 0 : count;
    }
}
