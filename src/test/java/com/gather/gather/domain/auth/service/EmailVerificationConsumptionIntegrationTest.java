package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class EmailVerificationConsumptionIntegrationTest {

    private static final String EMAIL = "email-consumption@example.com";

    @Autowired private EmailVerificationRequirementService requirementService;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private UUID verificationId;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        verificationId = UUID.randomUUID();
        executorService = Executors.newFixedThreadPool(2);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            EmailVerification verification =
                                    EmailVerification.create(
                                            EMAIL,
                                            verificationId.toString(),
                                            "a".repeat(64),
                                            now.plusMinutes(10),
                                            now.minusMinutes(2));
                            verification.verify(now.minusMinutes(1));
                            emailVerificationRepository.save(verification);
                        });
    }

    @AfterEach
    void cleanUp() throws Exception {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                emailVerificationRepository
                                        .findByEmail(EMAIL)
                                        .ifPresent(emailVerificationRepository::delete));
    }

    @Test
    @DisplayName("성공한 이메일 인증 소비는 저장되고 같은 ID 재사용은 거부된다")
    void consumeForSignup_preventsReuse() {
        consume();

        assertThat(emailVerificationRepository.findByVerificationId(verificationId.toString()))
                .isEmpty();
        assertThatThrownBy(this::consume)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_VERIFICATION_REQUIRED));
    }

    @Test
    @DisplayName("다른 이메일은 인증 ID를 소비할 수 없다")
    void consumeForSignup_rejectsEmailMismatch() {
        assertThatThrownBy(
                        () ->
                                transactionTemplate()
                                        .executeWithoutResult(
                                                status ->
                                                        requirementService.consumeForSignup(
                                                                "other@example.com",
                                                                verificationId)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_VERIFICATION_REQUIRED));
    }

    @Test
    @DisplayName("동일 이메일 인증 ID의 동시 소비는 정확히 하나만 성공한다")
    void consumeForSignup_allowsOnlyOneConcurrentConsumer() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> first = executorService.submit(() -> consumeAfterBarrier(ready, start));
        Future<Boolean> second = executorService.submit(() -> consumeAfterBarrier(ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(emailVerificationRepository.findByVerificationId(verificationId.toString()))
                .isEmpty();
    }

    private void consume() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> requirementService.consumeForSignup(EMAIL, verificationId));
    }

    private boolean consumeAfterBarrier(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            consume();
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
            return false;
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
