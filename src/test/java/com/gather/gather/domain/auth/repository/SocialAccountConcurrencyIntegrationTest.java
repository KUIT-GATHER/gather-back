package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.service.SocialAccountConstraint;
import com.gather.gather.domain.auth.service.SocialAccountConstraintResolver;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SocialAccountConcurrencyIntegrationTest {

    private static final String PROVIDER_USER_KEY = "e".repeat(64);
    private static final String LEGACY_PROVIDER_USER_ID = "concurrent-kakao-20260729";

    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SocialAccountConstraintResolver constraintResolver;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private Long firstUserId;
    private Long secondUserId;

    @BeforeEach
    void setUp() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(
                status -> {
                    firstUserId = saveUser("01091000001", "concurrentsocialone").getId();
                    secondUserId = saveUser("01091000002", "concurrentsocialtwo").getId();
                });
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(
                status -> {
                    socialAccountRepository
                            .findByProviderAndProviderUserKey(
                                    SocialProvider.KAKAO, PROVIDER_USER_KEY)
                            .ifPresent(socialAccountRepository::delete);
                    socialAccountRepository.flush();
                    userRepository.deleteAllById(List.of(firstUserId, secondUserId));
                    userRepository.flush();
                });
    }

    @Test
    @DisplayName("동일 카카오 계정 최초 INSERT 경쟁은 row 하나만 만들고 충돌 요청이 재조회한다")
    void concurrentFirstInsert_createsOneRowAndReloadsConflict() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<InsertResult> first = submitInsert(firstUserId, ready, start);
        Future<InsertResult> second = submitInsert(secondUserId, ready, start);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(InsertResult.CREATED, InsertResult.CONFLICT_RELOADED);
        assertThat(
                        socialAccountRepository.findByProviderAndProviderUserKey(
                                SocialProvider.KAKAO, PROVIDER_USER_KEY))
                .isPresent();
    }

    private Future<InsertResult> submitInsert(
            Long userId, CountDownLatch ready, CountDownLatch start) {
        return executorService.submit(
                () -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 INSERT 시작 신호를 받지 못했습니다.");
                    }
                    try {
                        new TransactionTemplate(transactionManager)
                                .executeWithoutResult(
                                        status -> {
                                            User user =
                                                    userRepository.findById(userId).orElseThrow();
                                            socialAccountRepository.saveAndFlush(
                                                    SocialAccount.createLinked(
                                                            user,
                                                            SocialProvider.KAKAO,
                                                            LEGACY_PROVIDER_USER_ID,
                                                            PROVIDER_USER_KEY,
                                                            1,
                                                            new EncryptedProviderUserId(
                                                                    "ciphertext-" + userId, 1),
                                                            LocalDateTime.of(2026, 7, 29, 12, 0)));
                                        });
                        return InsertResult.CREATED;
                    } catch (DataIntegrityViolationException exception) {
                        SocialAccountConstraint constraint = constraintResolver.resolve(exception);
                        if (constraint != SocialAccountConstraint.PROVIDER_USER_KEY
                                && constraint != SocialAccountConstraint.LEGACY_PROVIDER_USER_ID) {
                            return InsertResult.UNEXPECTED_CONSTRAINT;
                        }
                        return socialAccountRepository
                                        .findByProviderAndProviderUserKey(
                                                SocialProvider.KAKAO, PROVIDER_USER_KEY)
                                        .isPresent()
                                ? InsertResult.CONFLICT_RELOADED
                                : InsertResult.CONFLICT_NOT_FOUND;
                    }
                });
    }

    private User saveUser(String phoneNumber, String nickname) {
        return userRepository.saveAndFlush(
                User.createSocial(
                        "홍길동",
                        LocalDate.of(2002, 3, 15),
                        Gender.MALE,
                        phoneNumber,
                        nickname,
                        null,
                        true,
                        true,
                        false,
                        null,
                        List.of(PostingCategory.WELFARE)));
    }

    private enum InsertResult {
        CREATED,
        CONFLICT_RELOADED,
        CONFLICT_NOT_FOUND,
        UNEXPECTED_CONSTRAINT
    }
}
