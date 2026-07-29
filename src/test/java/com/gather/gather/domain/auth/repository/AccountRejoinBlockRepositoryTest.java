package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.AccountRejoinBlock;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AccountRejoinBlockRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 12, 0);

    @Autowired private AccountRejoinBlockRepository accountRejoinBlockRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<Long> blockIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> regionIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            blockIds.forEach(
                                    id ->
                                            accountRejoinBlockRepository
                                                    .findById(id)
                                                    .ifPresent(
                                                            accountRejoinBlockRepository::delete));
                            userIds.forEach(
                                    id ->
                                            userRepository
                                                    .findById(id)
                                                    .ifPresent(userRepository::delete));
                            regionIds.forEach(
                                    id ->
                                            regionRepository
                                                    .findById(id)
                                                    .ifPresent(regionRepository::delete));
                        });
    }

    @Test
    void findByIdentifierTypeAndIdentifierHash_returnsSavedBlock() {
        Fixture fixture = createFixture(AccountRejoinBlockIdentifierType.PHONE, hash());

        AccountRejoinBlock found =
                transactionTemplate()
                        .execute(
                                status ->
                                        accountRejoinBlockRepository
                                                .findByIdentifierTypeAndIdentifierHash(
                                                        fixture.block().getIdentifierType(),
                                                        fixture.block().getIdentifierHash())
                                                .orElseThrow());

        assertThat(found.getId()).isEqualTo(fixture.block().getId());
    }

    @Test
    void findByIdentifierForUpdate_returnsSavedBlockInsideTransaction() {
        Fixture fixture = createFixture(AccountRejoinBlockIdentifierType.PHONE, hash());

        AccountRejoinBlock found =
                transactionTemplate()
                        .execute(
                                status ->
                                        accountRejoinBlockRepository
                                                .findByIdentifierForUpdate(
                                                        fixture.block().getIdentifierType(),
                                                        fixture.block().getIdentifierHash())
                                                .orElseThrow());

        assertThat(found.getId()).isEqualTo(fixture.block().getId());
    }

    @Test
    void uniqueConstraint_rejectsSameTypeAndHash() {
        String identifierHash = hash();

        assertThatThrownBy(
                        () ->
                                transactionTemplate()
                                        .executeWithoutResult(
                                                status -> {
                                                    User user = saveUser();
                                                    AccountRejoinBlock first =
                                                            saveBlock(
                                                                    user.getId(),
                                                                    AccountRejoinBlockIdentifierType
                                                                            .PHONE,
                                                                    identifierHash);
                                                    accountRejoinBlockRepository.saveAndFlush(
                                                            first);

                                                    AccountRejoinBlock duplicate =
                                                            block(
                                                                    user.getId(),
                                                                    AccountRejoinBlockIdentifierType
                                                                            .PHONE,
                                                                    identifierHash);
                                                    accountRejoinBlockRepository.saveAndFlush(
                                                            duplicate);
                                                }))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraint_allowsSameHashForDifferentTypes() {
        String identifierHash = hash();

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            User user = saveUser();
                            accountRejoinBlockRepository.saveAndFlush(
                                    saveBlock(
                                            user.getId(),
                                            AccountRejoinBlockIdentifierType.PHONE,
                                            identifierHash));
                            accountRejoinBlockRepository.saveAndFlush(
                                    saveBlock(
                                            user.getId(),
                                            AccountRejoinBlockIdentifierType.KAKAO,
                                            identifierHash));
                        });

        long matchingBlockCount =
                transactionTemplate()
                        .execute(
                                status ->
                                        accountRejoinBlockRepository.findAll().stream()
                                                .filter(
                                                        block ->
                                                                block.getIdentifierHash()
                                                                        .equals(identifierHash))
                                                .count());

        assertThat(matchingBlockCount).isEqualTo(2);
    }

    @Test
    void findByIdentifierForUpdate_serializesConcurrentAccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        Fixture fixture = createFixture(AccountRejoinBlockIdentifierType.PHONE, hash());
        CountDownLatch firstTransactionLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> first =
                    executor.submit(
                            () ->
                                    transactionTemplate()
                                            .execute(
                                                    status -> {
                                                        accountRejoinBlockRepository
                                                                .findByIdentifierForUpdate(
                                                                        fixture.block()
                                                                                .getIdentifierType(),
                                                                        fixture.block()
                                                                                .getIdentifierHash())
                                                                .orElseThrow();
                                                        firstTransactionLocked.countDown();
                                                        await(releaseFirstTransaction);
                                                        return null;
                                                    }));
            assertThat(firstTransactionLocked.await(3, TimeUnit.SECONDS)).isTrue();

            Future<AccountRejoinBlock> second =
                    executor.submit(
                            () ->
                                    transactionTemplate()
                                            .execute(
                                                    status ->
                                                            accountRejoinBlockRepository
                                                                    .findByIdentifierForUpdate(
                                                                            fixture.block()
                                                                                    .getIdentifierType(),
                                                                            fixture.block()
                                                                                    .getIdentifierHash())
                                                                    .orElseThrow()));

            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFirstTransaction.countDown();
            first.get(3, TimeUnit.SECONDS);
            assertThat(second.get(3, TimeUnit.SECONDS).getId()).isEqualTo(fixture.block().getId());
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    private Fixture createFixture(
            AccountRejoinBlockIdentifierType identifierType, String identifierHash) {
        return transactionTemplate()
                .execute(
                        status -> {
                            User user = saveUser();
                            AccountRejoinBlock block =
                                    accountRejoinBlockRepository.saveAndFlush(
                                            saveBlock(
                                                    user.getId(), identifierType, identifierHash));
                            return new Fixture(user, block);
                        });
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Region region =
                regionRepository.save(Region.create("테스트구", 2, "rejoin-region-" + suffix, null));
        regionIds.add(region.getId());
        User user =
                userRepository.save(
                        User.create(
                                "홍길동",
                                LocalDate.of(2000, 1, 1),
                                Gender.MALE,
                                "010" + suffix,
                                suffix + "@example.com",
                                "encoded-password",
                                "사용자" + suffix,
                                null,
                                true,
                                true,
                                false,
                                region,
                                List.of(PostingCategory.WELFARE)));
        userIds.add(user.getId());
        return user;
    }

    private AccountRejoinBlock saveBlock(
            Long sourceUserId,
            AccountRejoinBlockIdentifierType identifierType,
            String identifierHash) {
        AccountRejoinBlock block = block(sourceUserId, identifierType, identifierHash);
        AccountRejoinBlock saved = accountRejoinBlockRepository.save(block);
        blockIds.add(saved.getId());
        return saved;
    }

    private AccountRejoinBlock block(
            Long sourceUserId,
            AccountRejoinBlockIdentifierType identifierType,
            String identifierHash) {
        return AccountRejoinBlock.create(
                identifierType, identifierHash, 1, NOW.plusDays(7), sourceUserId, NOW);
    }

    private String hash() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("잠금 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("잠금 테스트 대기가 중단되었습니다.", exception);
        }
    }

    private record Fixture(User user, AccountRejoinBlock block) {}
}
