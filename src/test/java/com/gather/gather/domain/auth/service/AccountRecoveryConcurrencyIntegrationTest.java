package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.dto.AccountRecoveryRequest;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
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

    private static final String PHONE_NUMBER = "01095550104";

    @Autowired private AccountRecoveryService accountRecoveryService;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private UUID verificationId;
    private Long userId;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        verificationId = UUID.randomUUID();
        userId = null;
        executorService = Executors.newFixedThreadPool(2);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            PhoneVerification verification =
                                    PhoneVerification.create(
                                            verificationId.toString(),
                                            PHONE_NUMBER,
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
        if (userId != null) {
            transactionTemplate().executeWithoutResult(status -> userRepository.deleteById(userId));
        }
    }

    @Test
    @DisplayName("동일 FIND_ACCOUNT verificationId는 동시 요청 중 한 번만 계정 조회 결과까지 진행한다")
    void recoverEmail_allowsOnlyOneConcurrentConsumption() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<RecoveryAttempt> first =
                executorService.submit(() -> recoverAfterBarrier(ready, start));
        Future<RecoveryAttempt> second =
                executorService.submit(() -> recoverAfterBarrier(ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(
                        RecoveryAttempt.failure(ErrorCode.ACCOUNT_NOT_FOUND),
                        RecoveryAttempt.failure(ErrorCode.PHONE_VERIFICATION_REQUIRED));
        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(verificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("매칭되는 EMAIL 계정도 동일 verificationId 동시 요청에서 한 번만 반환한다")
    void recoverEmail_allowsOnlyOneConcurrentEmailResult() throws Exception {
        userId =
                transactionTemplate()
                        .execute(
                                status ->
                                        userRepository
                                                .save(
                                                        User.create(
                                                                "동시복구회원",
                                                                LocalDate.of(2000, 1, 1),
                                                                Gender.FEMALE,
                                                                PHONE_NUMBER,
                                                                "recovery-concurrency@example.com",
                                                                "encoded-password",
                                                                "동시복구",
                                                                null,
                                                                true,
                                                                true,
                                                                false,
                                                                null,
                                                                List.of()))
                                                .getId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<RecoveryAttempt> first =
                executorService.submit(() -> recoverAfterBarrier(ready, start));
        Future<RecoveryAttempt> second =
                executorService.submit(() -> recoverAfterBarrier(ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(
                        RecoveryAttempt.success(AccountLoginType.EMAIL),
                        RecoveryAttempt.failure(ErrorCode.PHONE_VERIFICATION_REQUIRED));
        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(verificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNotNull();
    }

    private RecoveryAttempt recoverAfterBarrier(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            return RecoveryAttempt.success(
                    accountRecoveryService
                            .recoverEmail(new AccountRecoveryRequest(verificationId))
                            .loginType());
        } catch (BusinessException exception) {
            return RecoveryAttempt.failure(exception.getErrorCode());
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private record RecoveryAttempt(AccountLoginType loginType, ErrorCode errorCode) {

        private static RecoveryAttempt success(AccountLoginType loginType) {
            return new RecoveryAttempt(loginType, null);
        }

        private static RecoveryAttempt failure(ErrorCode errorCode) {
            return new RecoveryAttempt(null, errorCode);
        }
    }
}
