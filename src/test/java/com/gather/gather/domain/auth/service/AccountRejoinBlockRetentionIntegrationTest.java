package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.AccountRejoinBlock;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.AccountRejoinBlockRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 재가입 후 재탈퇴한 식별자가 최초 탈퇴 기준으로 조기 파기되지 않는지 실제 upsert·파기 쿼리로 검증한다. */
@SpringBootTest
class AccountRejoinBlockRetentionIntegrationTest {

    @Autowired private AccountRejoinBlockService rejoinBlockService;
    @Autowired private AccountRejoinBlockRepository blockRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private final List<Long> userIds = new ArrayList<>();

    private LocalDateTime now;
    private String phoneNumber;
    private RejoinBlockIdentifier identifier;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        // DATETIME(6)은 마이크로초까지만 저장하고 나머지를 반올림하므로 기준 시각을 미리 맞춘다.
        now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        phoneNumber = "010" + suffix;
        identifier =
                new RejoinBlockIdentifier(
                        AccountRejoinBlockIdentifierType.PHONE,
                        UUID.randomUUID().toString().replace("-", "").repeat(2),
                        1);
        executorService = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            blockRepository
                                    .findByIdentifierTypeAndIdentifierHash(
                                            identifier.type(), identifier.hash())
                                    .ifPresent(blockRepository::delete);
                            blockRepository.flush();
                            userIds.forEach(
                                    id ->
                                            userRepository
                                                    .findById(id)
                                                    .ifPresent(userRepository::delete));
                        });
        userIds.clear();
    }

    @Test
    @DisplayName("재탈퇴한 식별자는 최초 탈퇴가 아니라 최신 탈퇴 완료 시각을 기준으로 파기된다")
    void cleanup_followsLatestWithdrawalAfterRejoin() {
        LocalDateTime firstWithdrawnAt = now.minusMonths(4);
        LocalDateTime secondWithdrawnAt = now.minusMonths(1);
        Long firstUserId = withdrawAndAnonymize(phoneNumber, firstWithdrawnAt);
        createOrExtendBlock(firstUserId, firstWithdrawnAt);
        Long secondUserId = withdraw(phoneNumber, secondWithdrawnAt);
        createOrExtendBlock(secondUserId, secondWithdrawnAt);

        AccountRejoinBlock block = findBlock();
        assertThat(block.getSourceUserId()).isEqualTo(secondUserId);
        assertThat(block.getKeyVersion()).isEqualTo(identifier.keyVersion());
        assertThat(block.getExpiresAt()).isEqualTo(secondWithdrawnAt.plusDays(7));
        assertThat(block.getCreatedAt()).isEqualTo(firstWithdrawnAt);

        assertThat(cleanupAt(now)).isZero();
        assertThat(blockRepository.existsById(block.getId())).isTrue();

        assertThat(cleanupAt(secondWithdrawnAt.plusMonths(3))).isEqualTo(1);
        assertThat(blockRepository.existsById(block.getId())).isFalse();
    }

    @Test
    @DisplayName("보관기간이 지난 row의 파기와 최신 탈퇴 upsert가 경합해도 새 차단은 삭제되지 않는다")
    void cleanup_doesNotDeleteBlockRefreshedByConcurrentWithdrawal() throws Exception {
        LocalDateTime firstWithdrawnAt = now.minusMonths(4);
        Long firstUserId = withdrawAndAnonymize(phoneNumber, firstWithdrawnAt);
        createOrExtendBlock(firstUserId, firstWithdrawnAt);
        Long secondUserId = withdraw(phoneNumber, now);

        Future<Integer> cleanup =
                transactionTemplate()
                        .execute(
                                status -> {
                                    rejoinBlockService.createOrExtendBlock(
                                            identifier, secondUserId, now);
                                    Future<Integer> submitted =
                                            executorService.submit(() -> cleanupAt(now));
                                    assertThatThrownBy(
                                                    () -> submitted.get(300, TimeUnit.MILLISECONDS))
                                            .isInstanceOf(TimeoutException.class);
                                    return submitted;
                                });

        assertThat(cleanup.get(5, TimeUnit.SECONDS)).isZero();
        AccountRejoinBlock refreshed = findBlock();
        assertThat(refreshed.getSourceUserId()).isEqualTo(secondUserId);
        assertThat(refreshed.getExpiresAt()).isEqualTo(now.plusDays(7));
    }

    private void createOrExtendBlock(Long sourceUserId, LocalDateTime withdrawnAt) {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                rejoinBlockService.createOrExtendBlock(
                                        identifier, sourceUserId, withdrawnAt));
    }

    private int cleanupAt(LocalDateTime cleanupNow) {
        return transactionTemplate()
                .execute(status -> blockRepository.deleteAllRetentionExpired(cleanupNow));
    }

    private AccountRejoinBlock findBlock() {
        return transactionTemplate()
                .execute(
                        status ->
                                blockRepository
                                        .findByIdentifierTypeAndIdentifierHash(
                                                identifier.type(), identifier.hash())
                                        .orElseThrow());
    }

    private Long withdraw(String userPhoneNumber, LocalDateTime withdrawnAt) {
        return transactionTemplate()
                .execute(
                        status -> {
                            User user = saveUser(userPhoneNumber);
                            user.withdraw(WithdrawalReason.SELF, withdrawnAt);
                            userRepository.flush();
                            return user.getId();
                        });
    }

    /** 재가입이 같은 전화번호로 이뤄지도록 최초 탈퇴자는 실제 탈퇴 흐름과 같이 익명화한다. */
    private Long withdrawAndAnonymize(String userPhoneNumber, LocalDateTime withdrawnAt) {
        return transactionTemplate()
                .execute(
                        status -> {
                            User user = saveUser(userPhoneNumber);
                            user.withdraw(WithdrawalReason.SELF, withdrawnAt);
                            user.anonymize(withdrawnAt);
                            userRepository.flush();
                            return user.getId();
                        });
    }

    private User saveUser(String userPhoneNumber) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User user =
                userRepository.saveAndFlush(
                        User.create(
                                "재가입회원",
                                LocalDate.of(2000, 1, 1),
                                Gender.MALE,
                                userPhoneNumber,
                                suffix + "@example.com",
                                "encoded-password",
                                "재가입" + suffix,
                                null,
                                true,
                                true,
                                false,
                                null,
                                List.of()));
        userIds.add(user.getId());
        return user;
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
