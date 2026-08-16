package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
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
class PhoneVerificationConsumptionIntegrationTest {

    private static final String PHONE_NUMBER = "01095550003";

    @Autowired private PhoneVerificationRequirementService requirementService;
    @Autowired private PhoneVerificationTransactionService transactionService;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private UUID verificationId;
    private UUID reservationVerificationId;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        verificationId = UUID.randomUUID();
        executorService = Executors.newFixedThreadPool(2);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            PhoneVerification verification =
                                    PhoneVerification.create(
                                            verificationId.toString(),
                                            PHONE_NUMBER,
                                            PhoneVerificationPurpose.SIGNUP,
                                            "GATHER-CONSUME001",
                                            now.plusMinutes(5),
                                            now.minusMinutes(1));
                            verification.verify(now.minusMinutes(1));
                            phoneVerificationRepository.save(verification);
                        });
    }

    @AfterEach
    void cleanUp() throws Exception {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                phoneVerificationRepository
                                        .findByVerificationId(verificationId.toString())
                                        .ifPresent(phoneVerificationRepository::delete));
        if (reservationVerificationId != null) {
            transactionTemplate()
                    .executeWithoutResult(
                            status ->
                                    phoneVerificationRepository
                                            .findByVerificationId(
                                                    reservationVerificationId.toString())
                                            .ifPresent(phoneVerificationRepository::delete));
        }
    }

    @Test
    @DisplayName("성공한 소비는 저장되고 같은 인증 ID의 재사용은 거부된다")
    void consumeForSignup_preventsReuse() {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                requirementService.consumeForSignup(verificationId, PHONE_NUMBER));

        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(verificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNotNull();
        assertThatThrownBy(
                        () ->
                                transactionTemplate()
                                        .executeWithoutResult(
                                                status ->
                                                        requirementService.consumeForSignup(
                                                                verificationId, PHONE_NUMBER)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.PHONE_VERIFICATION_REQUIRED));
    }

    @Test
    @DisplayName("동일 인증 ID의 동시 소비 요청은 행 잠금으로 하나만 성공한다")
    void consumeForSignup_allowsOnlyOneConcurrentConsumer() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> first = executorService.submit(() -> consumeAfterBarrier(ready, start));
        Future<Boolean> second = executorService.submit(() -> consumeAfterBarrier(ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
    }

    @Test
    @DisplayName("동일 인증 ID의 동시 confirm 예약은 하나만 외부 호출 권한을 얻는다")
    void reserveConfirm_allowsOnlyOneConcurrentProviderCall() throws Exception {
        reservationVerificationId = UUID.randomUUID();
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            phoneVerificationRepository.save(
                                    PhoneVerification.create(
                                            reservationVerificationId.toString(),
                                            "01095550004",
                                            PhoneVerificationPurpose.SIGNUP,
                                            "GATHER-CONFIRM001",
                                            now.plusMinutes(5),
                                            now.minusMinutes(1)));
                        });
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> first =
                executorService.submit(() -> reserveConfirmAfterBarrier(ready, start));
        Future<Boolean> second =
                executorService.submit(() -> reserveConfirmAfterBarrier(ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(reservationVerificationId.toString())
                                .orElseThrow()
                                .getConfirmAttemptCount())
                .isEqualTo(1);
    }

    private boolean consumeAfterBarrier(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            transactionTemplate()
                    .executeWithoutResult(
                            status ->
                                    requirementService.consumeForSignup(
                                            verificationId, PHONE_NUMBER));
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PHONE_VERIFICATION_REQUIRED);
            return false;
        }
    }

    private boolean reserveConfirmAfterBarrier(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            transactionService.reserveConfirm(reservationVerificationId.toString());
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.PHONE_VERIFICATION_RATE_LIMITED);
            return false;
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
