package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.AccountRejoinBlock;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 파기 경계는 실행 시각에 따라 달라지므로 Clock 대신 기준 시각을 직접 넘기는 repository 계층에서 검증한다. 기준 시각은 3개월 뒤가 월말 보정되는 날짜로
 * 고정했다(2026-01-31 + 3개월 = 2026-04-30).
 */
@SpringBootTest
class AccountRejoinBlockCleanupRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 9, 0);

    @Autowired private AccountRejoinBlockRepository accountRejoinBlockRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<Long> blockIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    private Long exactlyThreeMonthsBlockId;
    private Long monthEndBlockId;
    private Long justBeforeThreeMonthsBlockId;
    private Long activeBlockId;
    private Long rejoinAllowedButRetainedBlockId;
    private Long withdrawalPendingBlockId;

    @BeforeEach
    void setUp() {
        // 정확히 3개월이 지난 PHONE row
        exactlyThreeMonthsBlockId =
                saveWithdrawnBlock(
                        AccountRejoinBlockIdentifierType.PHONE,
                        NOW.minusMonths(3),
                        NOW.minusMonths(3).plusDays(7));
        // 월말 탈퇴는 90일이 아니라 달력 기준 보정(1/31 + 3개월 = 4/30)이 적용돼야 한다.
        monthEndBlockId =
                saveWithdrawnBlock(
                        AccountRejoinBlockIdentifierType.KAKAO,
                        LocalDateTime.of(2026, 1, 31, 9, 0),
                        LocalDateTime.of(2026, 2, 7, 9, 0));
        justBeforeThreeMonthsBlockId =
                saveWithdrawnBlock(
                        AccountRejoinBlockIdentifierType.PHONE,
                        NOW.minusMonths(3).plusSeconds(1),
                        NOW.minusMonths(3).plusDays(7));
        // 보관기간은 지났지만 차단이 살아 있는 비정상 row는 안전장치로 보호한다.
        activeBlockId =
                saveWithdrawnBlock(
                        AccountRejoinBlockIdentifierType.PHONE, NOW.minusYears(1), NOW.plusDays(1));
        // 재가입은 이미 가능하지만 보관기간은 남아 있는 정상 row
        rejoinAllowedButRetainedBlockId =
                saveWithdrawnBlock(
                        AccountRejoinBlockIdentifierType.KAKAO,
                        NOW.minusDays(10),
                        NOW.minusDays(3));
        // 카카오 unlink 대기 중이라 탈퇴가 완료되지 않은 row
        withdrawalPendingBlockId =
                savePendingBlock(AccountRejoinBlockIdentifierType.KAKAO, NOW.minusYears(1));
    }

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
                        });
        blockIds.clear();
        userIds.clear();
    }

    @Test
    @DisplayName("탈퇴 완료 후 3개월이 지난 row만 파기하고 반복 실행은 멱등하다")
    void deleteAllRetentionExpired_deletesBlocksThreeCalendarMonthsAfterWithdrawal() {
        int deleted = cleanupAt(NOW);

        assertThat(deleted).isEqualTo(2);
        assertThat(accountRejoinBlockRepository.existsById(exactlyThreeMonthsBlockId)).isFalse();
        assertThat(accountRejoinBlockRepository.existsById(monthEndBlockId)).isFalse();
        assertThat(cleanupAt(NOW)).isZero();
    }

    @Test
    @DisplayName("보관기간 전이거나 차단이 유효하거나 탈퇴가 완료되지 않은 row는 보존한다")
    void deleteAllRetentionExpired_keepsBlocksOutsideRetentionCondition() {
        cleanupAt(NOW);

        assertThat(accountRejoinBlockRepository.existsById(justBeforeThreeMonthsBlockId)).isTrue();
        assertThat(accountRejoinBlockRepository.existsById(activeBlockId)).isTrue();
        assertThat(accountRejoinBlockRepository.existsById(rejoinAllowedButRetainedBlockId))
                .isTrue();
        assertThat(accountRejoinBlockRepository.existsById(withdrawalPendingBlockId)).isTrue();
    }

    private int cleanupAt(LocalDateTime now) {
        return transactionTemplate()
                .execute(status -> accountRejoinBlockRepository.deleteAllRetentionExpired(now));
    }

    private Long saveWithdrawnBlock(
            AccountRejoinBlockIdentifierType identifierType,
            LocalDateTime withdrawnAt,
            LocalDateTime expiresAt) {
        return transactionTemplate()
                .execute(
                        status -> {
                            User user = saveUser();
                            user.withdraw(WithdrawalReason.SELF, withdrawnAt);
                            userRepository.flush();
                            return saveBlock(user.getId(), identifierType, withdrawnAt, expiresAt);
                        });
    }

    private Long savePendingBlock(
            AccountRejoinBlockIdentifierType identifierType, LocalDateTime requestedAt) {
        return transactionTemplate()
                .execute(
                        status -> {
                            User user = saveUser();
                            user.requestWithdrawal(WithdrawalReason.SELF, requestedAt);
                            userRepository.flush();
                            return saveBlock(
                                    user.getId(),
                                    identifierType,
                                    requestedAt,
                                    requestedAt.plusDays(7));
                        });
    }

    private Long saveBlock(
            Long sourceUserId,
            AccountRejoinBlockIdentifierType identifierType,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        AccountRejoinBlock block =
                accountRejoinBlockRepository.saveAndFlush(
                        AccountRejoinBlock.create(
                                identifierType, hash(), 1, expiresAt, sourceUserId, createdAt));
        blockIds.add(block.getId());
        return block.getId();
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        User user =
                userRepository.save(
                        User.create(
                                "보관기간회원",
                                LocalDate.of(2000, 1, 1),
                                Gender.MALE,
                                "010" + suffix,
                                suffix + "@example.com",
                                "encoded-password",
                                "보관" + suffix,
                                null,
                                true,
                                true,
                                false,
                                null,
                                List.of()));
        userIds.add(user.getId());
        return user;
    }

    private String hash() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
