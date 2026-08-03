package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.AccountIdentityGuard;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AccountIdentityGuardRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 14, 30);
    private final String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);

    @Autowired private AccountIdentityGuardRepository guardRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        deleteGuard(3);
        deleteGuard(4);
    }

    @Test
    void upsertCreatesOneDurableRowForTheSameIdentity() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            guardRepository.upsert("PHONE", hash, 3, NOW);
                            guardRepository.upsert("PHONE", hash, 3, NOW.plusHours(1));
                        });

        assertThat(matchingGuards()).hasSize(1);
    }

    @Test
    void uniqueIdentityIncludesKeyVersion() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            guardRepository.upsert("PHONE", hash, 3, NOW);
                            guardRepository.upsert("PHONE", hash, 4, NOW);
                        });

        assertThat(matchingGuards()).hasSize(2);
    }

    @Test
    void selectForUpdateSerializesConcurrentAccess()
            throws InterruptedException, ExecutionException, TimeoutException {
        transactionTemplate()
                .executeWithoutResult(status -> guardRepository.upsert("PHONE", hash, 3, NOW));
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> first =
                    executor.submit(
                            () ->
                                    transactionTemplate()
                                            .execute(
                                                    status -> {
                                                        findForUpdate();
                                                        firstLocked.countDown();
                                                        await(releaseFirst);
                                                        return null;
                                                    }));
            assertThat(firstLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<AccountIdentityGuard> second =
                    executor.submit(() -> transactionTemplate().execute(status -> findForUpdate()));
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            first.get(3, TimeUnit.SECONDS);
            assertThat(second.get(3, TimeUnit.SECONDS).getIdentityHash()).isEqualTo(hash);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private AccountIdentityGuard findForUpdate() {
        return guardRepository
                .findByIdentityForUpdate(AccountRejoinBlockIdentifierType.PHONE, hash, 3)
                .orElseThrow();
    }

    private java.util.List<AccountIdentityGuard> matchingGuards() {
        return guardRepository.findAll().stream()
                .filter(guard -> guard.getIdentityType() == AccountRejoinBlockIdentifierType.PHONE)
                .filter(guard -> guard.getIdentityHash().equals(hash))
                .toList();
    }

    private void deleteGuard(int keyVersion) {
        jdbcTemplate.update(
                """
                delete from account_identity_guard
                where identity_type = ?
                  and key_version = ?
                  and identity_hash = ?
                """,
                AccountRejoinBlockIdentifierType.PHONE.name(),
                keyVersion,
                hash);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("guard 잠금 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("guard 잠금 테스트 대기가 중단되었습니다.", exception);
        }
    }
}
