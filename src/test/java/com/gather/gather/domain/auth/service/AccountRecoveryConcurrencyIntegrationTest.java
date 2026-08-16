package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.dto.AccountRecoveryRequest;
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
class AccountRecoveryConcurrencyIntegrationTest {

    @Autowired private AccountRecoveryService accountRecoveryService;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
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
                            PhoneVerification verification =
                                    PhoneVerification.create(
                                            verificationId.toString(),
                                            "01095550104",
                                            PhoneVerificationPurpose.FIND_ACCOUNT,
                                            "GATHER-RECOVERY03",
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
    }

    @Test
    @DisplayName("동일 FIND_ACCOUNT verificationId는 동시 요청 중 한 번만 계정 조회 결과까지 진행한다")
    void recoverEmail_allowsOnlyOneConcurrentConsumption() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<ErrorCode> first = executorService.submit(() -> recoverAfterBarrier(ready, start));
        Future<ErrorCode> second = executorService.submit(() -> recoverAfterBarrier(ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(
                        ErrorCode.ACCOUNT_NOT_FOUND, ErrorCode.PHONE_VERIFICATION_REQUIRED);
        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(verificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNotNull();
    }

    private ErrorCode recoverAfterBarrier(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            accountRecoveryService.recoverEmail(new AccountRecoveryRequest(verificationId));
            throw new AssertionError("계정이 없는 테스트에서는 성공할 수 없습니다.");
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
