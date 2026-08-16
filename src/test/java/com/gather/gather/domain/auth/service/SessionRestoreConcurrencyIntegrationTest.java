package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SessionRestoreConcurrencyIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private TokenProvider tokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long userId;
    private String rawRefreshToken;

    @AfterEach
    void cleanUp() {
        if (userId == null) {
            return;
        }
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            refreshTokenRepository.deleteAllByUserId(userId);
                            userRepository.deleteById(userId);
                        });
    }

    @Test
    void concurrentRestoreAllowsOneRotationAndReturnsAnonymousForTheOther() throws Exception {
        userId = transactionTemplate().execute(status -> createFixture());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<TokenIssueResult>> first =
                    executor.submit(() -> restoreAfterSignal(ready, start));
            Future<Optional<TokenIssueResult>> second =
                    executor.submit(() -> restoreAfterSignal(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Optional<TokenIssueResult>> results =
                    List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

            assertThat(results).filteredOn(Optional::isPresent).hasSize(1);
            assertThat(results).filteredOn(Optional::isEmpty).hasSize(1);
            assertSingleActiveTokenAfterRotation();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRestoreAndReissueKeepTheirDifferentFailureContracts() throws Exception {
        userId = transactionTemplate().execute(status -> createFixture());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<RotationOutcome> restore =
                    executor.submit(() -> restoreOutcomeAfterSignal(ready, start));
            Future<RotationOutcome> reissue =
                    executor.submit(() -> reissueOutcomeAfterSignal(ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            RotationOutcome restoreOutcome = restore.get(5, TimeUnit.SECONDS);
            RotationOutcome reissueOutcome = reissue.get(5, TimeUnit.SECONDS);

            assertThat(List.of(restoreOutcome, reissueOutcome))
                    .filteredOn(RotationOutcome::success)
                    .hasSize(1);
            if (restoreOutcome.success()) {
                assertThat(reissueOutcome.errorCode()).isEqualTo(ErrorCode.REVOKED_TOKEN);
            } else {
                assertThat(restoreOutcome.anonymous()).isTrue();
                assertThat(reissueOutcome.success()).isTrue();
            }
            assertSingleActiveTokenAfterRotation();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Optional<TokenIssueResult> restoreAfterSignal(
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return authService.restoreSession(rawRefreshToken);
    }

    private RotationOutcome restoreOutcomeAfterSignal(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        Optional<TokenIssueResult> result = restoreAfterSignal(ready, start);
        return result.isPresent() ? RotationOutcome.succeeded() : RotationOutcome.anonymousResult();
    }

    private RotationOutcome reissueOutcomeAfterSignal(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            authService.reissue(rawRefreshToken);
            return RotationOutcome.succeeded();
        } catch (BusinessException exception) {
            return RotationOutcome.failed(exception.getErrorCode());
        }
    }

    private Long createFixture() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        rawRefreshToken = "session-restore-" + UUID.randomUUID();
        User user =
                userRepository.save(
                        User.create(
                                "세션복원회원",
                                null,
                                null,
                                "010" + unique.replaceAll("[a-f]", "1"),
                                "session-restore-" + unique + "@example.com",
                                "encoded-password",
                                "복원" + unique,
                                null,
                                true,
                                true,
                                false,
                                null,
                                List.of()));
        refreshTokenRepository.save(
                RefreshToken.create(
                        tokenProvider.hashToken(rawRefreshToken),
                        user,
                        LocalDateTime.now().plusDays(1)));
        return user.getId();
    }

    private void assertSingleActiveTokenAfterRotation() {
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from refresh_token where user_id = ? and revoked = false",
                                Long.class,
                                userId))
                .isEqualTo(1L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from refresh_token where user_id = ?",
                                Long.class,
                                userId))
                .isEqualTo(2L);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private record RotationOutcome(boolean success, boolean anonymous, ErrorCode errorCode) {

        private static RotationOutcome succeeded() {
            return new RotationOutcome(true, false, null);
        }

        private static RotationOutcome anonymousResult() {
            return new RotationOutcome(false, true, null);
        }

        private static RotationOutcome failed(ErrorCode errorCode) {
            return new RotationOutcome(false, false, errorCode);
        }
    }
}
