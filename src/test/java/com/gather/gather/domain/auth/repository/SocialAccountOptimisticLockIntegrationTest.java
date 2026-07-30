package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.posting.entity.PostingCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SocialAccountOptimisticLockIntegrationTest {

    private static final LocalDateTime CONNECTED_AT = LocalDateTime.of(2026, 7, 30, 12, 0);

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long socialAccountId;
    private Long userId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            User user =
                                    userRepository.saveAndFlush(
                                            User.createSocial(
                                                    "홍길동",
                                                    LocalDate.of(2002, 3, 15),
                                                    Gender.MALE,
                                                    "01093000001",
                                                    "sociallockuser",
                                                    null,
                                                    true,
                                                    true,
                                                    false,
                                                    null,
                                                    List.of(PostingCategory.WELFARE)));
                            SocialAccount socialAccount =
                                    socialAccountRepository.saveAndFlush(
                                            SocialAccount.createLinked(
                                                    user,
                                                    SocialProvider.KAKAO,
                                                    "optimistic-kakao-20260730",
                                                    "f".repeat(64),
                                                    1,
                                                    new EncryptedProviderUserId(
                                                            "optimistic-ciphertext", 1),
                                                    CONNECTED_AT));
                            userId = user.getId();
                            socialAccountId = socialAccount.getId();
                        });
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            socialAccountRepository.deleteById(socialAccountId);
                            socialAccountRepository.flush();
                            userRepository.deleteById(userId);
                            userRepository.flush();
                        });
    }

    @Test
    @DisplayName("동시에 읽은 SocialAccount의 뒤늦은 상태 전이는 낙관적 락으로 거부한다")
    void concurrentStateTransition_rejectsStaleUpdate() {
        EntityManager firstEntityManager = entityManagerFactory.createEntityManager();
        EntityManager staleEntityManager = entityManagerFactory.createEntityManager();
        EntityTransaction firstTransaction = firstEntityManager.getTransaction();
        EntityTransaction staleTransaction = staleEntityManager.getTransaction();

        try {
            firstTransaction.begin();
            staleTransaction.begin();
            SocialAccount first = firstEntityManager.find(SocialAccount.class, socialAccountId);
            SocialAccount stale = staleEntityManager.find(SocialAccount.class, socialAccountId);
            first.markUnlinkPending(CONNECTED_AT.plusMinutes(1));
            stale.markUnlinkPending(CONNECTED_AT.plusMinutes(2));

            firstTransaction.commit();

            assertThatThrownBy(staleEntityManager::flush)
                    .isInstanceOf(OptimisticLockException.class);
        } finally {
            rollbackIfActive(firstTransaction);
            rollbackIfActive(staleTransaction);
            firstEntityManager.close();
            staleEntityManager.close();
        }
    }

    private void rollbackIfActive(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
    }
}
