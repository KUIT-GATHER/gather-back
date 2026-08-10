package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.dto.PhoneVerificationStartRequest;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class PhoneVerificationStartConcurrencyIntegrationTest {

    @Autowired private PhoneVerificationService phoneVerificationService;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ExecutorService executorService;
    private String phoneNumber;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(2);
        phoneNumber =
                "010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        RejoinBlockIdentifier identifier = identifierHasher.hashPhone(phoneNumber);
        jdbcTemplate.update("delete from phone_verification where phone_number = ?", phoneNumber);
        jdbcTemplate.update(
                "delete from account_identity_guard where identity_type = 'PHONE' and key_version = ? and identity_hash = ?",
                identifier.keyVersion(),
                identifier.hash());
    }

    @Test
    @DisplayName("같은 번호의 동시 인증 시작 요청은 하나의 세션만 생성한다")
    void start_concurrentRequestsForSamePhone_createsExactlyOneSession() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<StartOutcome> first = executorService.submit(() -> startAfterSignal(ready, start));
        Future<StartOutcome> second = executorService.submit(() -> startAfterSignal(ready, start));

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<StartOutcome> outcomes =
                Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

        assertThat(outcomes).filteredOn(StartOutcome::success).hasSize(1);
        assertThat(outcomes)
                .filteredOn(outcome -> !outcome.success())
                .extracting(StartOutcome::errorCode)
                .containsExactly(ErrorCode.PHONE_VERIFICATION_RATE_LIMITED);
        assertThat(countSessions()).isEqualTo(1L);
    }

    private StartOutcome startAfterSignal(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            phoneVerificationService.start(new PhoneVerificationStartRequest(phoneNumber));
            return StartOutcome.succeeded();
        } catch (BusinessException exception) {
            return StartOutcome.failed(exception.getErrorCode());
        }
    }

    private long countSessions() {
        Long count =
                jdbcTemplate.queryForObject(
                        "select count(*) from phone_verification where phone_number = ?",
                        Long.class,
                        phoneNumber);
        return count == null ? 0L : count;
    }

    private record StartOutcome(boolean success, ErrorCode errorCode) {

        private static StartOutcome succeeded() {
            return new StartOutcome(true, null);
        }

        private static StartOutcome failed(ErrorCode errorCode) {
            return new StartOutcome(false, errorCode);
        }
    }
}
