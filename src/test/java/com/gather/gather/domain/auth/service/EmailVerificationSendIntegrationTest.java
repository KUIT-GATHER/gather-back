package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.auth.dto.EmailVerificationSendRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 인증 코드 메일이 DB 커밋 이후에 발송되고, 발송 실패 시 저장된 코드 행이 보상 삭제되는지 검증한다. 커밋 후 발송·보상은 실제 트랜잭션 커밋 시점에만 동작하므로 통합
 * 테스트가 필요하다.
 */
@SpringBootTest
class EmailVerificationSendIntegrationTest {

    private static final String EMAIL = "reauth-send-test@example.com";

    @Autowired private AuthService authService;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private EmailVerificationCodeHasher emailVerificationCodeHasher;
    @MockitoBean private EmailSender emailSender;

    @BeforeEach
    @AfterEach
    void clean() {
        emailVerificationRepository
                .findByEmail(EMAIL)
                .ifPresent(emailVerificationRepository::delete);
    }

    @Test
    @DisplayName("발송이 성공하면 커밋 후 메일이 나가고 코드 행이 남는다")
    void send_success_persistsRowAndSendsAfterCommit() {
        authService.sendEmailVerificationCode(new EmailVerificationSendRequest(EMAIL));

        verify(emailSender).sendVerificationCode(eq(EMAIL), anyString());
        assertThat(emailVerificationRepository.findByEmail(EMAIL)).isPresent();
    }

    @Test
    @DisplayName("재발송은 같은 행의 인증 ID만 회전하고 이전 ID를 무효화한다")
    void resend_rotatesVerificationIdAndInvalidatesPreviousId() {
        authService.sendEmailVerificationCode(new EmailVerificationSendRequest(EMAIL));
        EmailVerification first = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        String previousVerificationId = first.getVerificationId();
        ReflectionTestUtils.setField(first, "createdAt", LocalDateTime.now().minusMinutes(5));
        emailVerificationRepository.saveAndFlush(first);

        authService.sendEmailVerificationCode(new EmailVerificationSendRequest(EMAIL));

        EmailVerification resent = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(resent.getId()).isEqualTo(first.getId());
        assertThat(resent.getVerificationId()).isNotEqualTo(previousVerificationId);
        assertThat(emailVerificationRepository.findByVerificationId(previousVerificationId))
                .isEmpty();
        assertThat(resent.getDailySendCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("최초 발송 요청 두 개가 동시에 실행되어도 한 건만 발송한다")
    void send_concurrentFirstRequests_sendsOnlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ErrorCode> first = executor.submit(() -> sendAfterSignal(ready, start));
            Future<ErrorCode> second = executor.submit(() -> sendAfterSignal(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<ErrorCode> results =
                    Arrays.asList(
                            first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(null, ErrorCode.EMAIL_RESEND_TOO_SOON);
            verify(emailSender, times(1)).sendVerificationCode(eq(EMAIL), anyString());
            assertThat(emailVerificationRepository.findByEmail(EMAIL)).isPresent();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("커밋 후 발송이 실패하면 EMAIL_SEND_FAILED와 함께 저장된 코드 행이 보상 삭제된다")
    void send_smtpFailureAfterCommit_compensatesByDeletingRow() {
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

    @Test
    @DisplayName("기존 행 재발송 실패 시 직전 상태를 복구하고 일일 발송 제한을 우회하지 못한다")
    void resend_smtpFailure_restoresPreviousStateAndPreservesDailyLimit() {
        LocalDateTime oldCreatedAt = LocalDateTime.now().minusMinutes(5).withNano(0);
        LocalDateTime oldExpiresAt = LocalDateTime.now().plusMinutes(5).withNano(0);
        LocalDateTime oldVerifiedAt = LocalDateTime.now().minusMinutes(4).withNano(0);
        LocalDateTime oldConsumedAt = LocalDateTime.now().minusMinutes(3).withNano(0);
        String oldVerificationId = UUID.randomUUID().toString();
        EmailVerification existing =
                EmailVerification.create(
                        EMAIL,
                        oldVerificationId,
                        emailVerificationCodeHasher.hash(oldVerificationId, "123456"),
                        oldExpiresAt,
                        oldCreatedAt);
        existing.verify(oldVerifiedAt);
        existing.consume(oldConsumedAt);
        existing.increaseAttempt();
        existing.increaseAttempt();
        ReflectionTestUtils.setField(existing, "createdAt", oldCreatedAt);
        ReflectionTestUtils.setField(existing, "dailySendCount", 4);
        emailVerificationRepository.saveAndFlush(existing);

        doThrow(new RuntimeException("smtp down"))
                .doNothing()
                .when(emailSender)
                .sendVerificationCode(anyString(), anyString());

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(EMAIL)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
                            assertThat(exception.getCause())
                                    .isInstanceOf(RuntimeException.class)
                                    .hasMessage("smtp down");
                        });

        EmailVerification restored = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(restored.getVerificationId()).isEqualTo(oldVerificationId);
        // verificationId와 codeHash는 HMAC 메시지로 묶여 있어, 둘 다 복원돼야 이전 코드가 다시 통한다.
        assertThat(restored.getCodeHash())
                .isEqualTo(emailVerificationCodeHasher.hash(oldVerificationId, "123456"));
        assertThat(restored.getCode()).isEmpty();
        assertThat(
                        emailVerificationCodeHasher.verify(
                                restored.getVerificationId(), "123456", restored.getCodeHash()))
                .isTrue();
        assertThat(restored.isVerified()).isTrue();
        assertThat(restored.getExpiresAt()).isEqualTo(oldExpiresAt);
        assertThat(restored.getVerifiedAt()).isEqualTo(oldVerifiedAt);
        assertThat(restored.getConsumedAt()).isEqualTo(oldConsumedAt);
        assertThat(restored.getCreatedAt()).isEqualTo(oldCreatedAt);
        assertThat(restored.getDailySendCount()).isEqualTo(4);
        assertThat(restored.getAttemptCount()).isEqualTo(2);

        authService.sendEmailVerificationCode(new EmailVerificationSendRequest(EMAIL));

        EmailVerification fifthSend = emailVerificationRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(fifthSend.getDailySendCount()).isEqualTo(5);
        ReflectionTestUtils.setField(fifthSend, "createdAt", LocalDateTime.now().minusMinutes(5));
        emailVerificationRepository.saveAndFlush(fifthSend);

        assertThatThrownBy(
                        () ->
                                authService.sendEmailVerificationCode(
                                        new EmailVerificationSendRequest(EMAIL)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_SEND_LIMIT_EXCEEDED));
        verify(emailSender, times(2)).sendVerificationCode(eq(EMAIL), anyString());
    }

    private ErrorCode sendAfterSignal(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            authService.sendEmailVerificationCode(new EmailVerificationSendRequest(EMAIL));
            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }
}
