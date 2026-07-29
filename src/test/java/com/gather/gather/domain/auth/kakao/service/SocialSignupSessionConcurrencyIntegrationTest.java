package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenService;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifierHasher;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SocialSignupSessionConcurrencyIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(9200000);

    @Autowired private KakaoSignupTransactionService signupTransactionService;
    @Autowired private SocialSignupSessionService signupSessionService;
    @Autowired private SocialSignupTokenService signupTokenService;
    @Autowired private SocialSignupSessionRepository signupSessionRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private SocialAccountProviderIdCipher providerIdCipher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @PersistenceContext private EntityManager entityManager;

    private ExecutorService executorService;
    private String providerUserId;
    private RejoinBlockIdentifier identifier;
    private long userCountBefore;
    private long socialAccountCountBefore;
    private long refreshTokenCountBefore;
    private final List<String> issuedTokens = new ArrayList<>();
    private final List<RejoinBlockIdentifier> trackedIdentifiers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(3);
        providerUserId = "signup-concurrency-" + SEQUENCE.incrementAndGet();
        identifier = identifierHasher.hashKakao(providerUserId);
        trackedIdentifiers.add(identifier);
        userCountBefore = userRepository.count();
        socialAccountCountBefore = socialAccountRepository.count();
        refreshTokenCountBefore = refreshTokenRepository.count();
    }

    @AfterEach
    void tearDown() throws Exception {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            trackedIdentifiers.stream()
                                    .map(
                                            tracked ->
                                                    socialAccountRepository
                                                            .findByProviderAndProviderUserKey(
                                                                    SocialProvider.KAKAO,
                                                                    tracked.hash()))
                                    .flatMap(java.util.Optional::stream)
                                    .forEach(
                                            account -> {
                                                Long userId = account.getUser().getId();
                                                refreshTokenRepository.findAll().stream()
                                                        .filter(
                                                                token ->
                                                                        token.getUser()
                                                                                .getId()
                                                                                .equals(userId))
                                                        .forEach(refreshTokenRepository::delete);
                                                socialAccountRepository.delete(account);
                                                socialAccountRepository.flush();
                                                userRepository.deleteById(userId);
                                            });
                            issuedTokens.stream()
                                    .map(signupTokenService::hashToken)
                                    .map(signupSessionRepository::findByTokenHash)
                                    .flatMap(java.util.Optional::stream)
                                    .forEach(signupSessionRepository::delete);
                            signupSessionRepository.flush();
                        });
    }

    @Test
    @DisplayName("동일 token 동시 소비는 User·SocialAccount·Refresh Token을 하나만 만든다")
    void sameTokenConcurrentConsumption_allowsOnlyOneSignup() throws Exception {
        String token = issueSession();

        List<AttemptResult> results =
                runConcurrently(
                        () -> consume(token, socialUser(1)), () -> consume(token, socialUser(2)));

        assertThat(results)
                .containsExactlyInAnyOrder(AttemptResult.SUCCESS, AttemptResult.REJECTED);
        assertSingleSignupCommitted();
    }

    @Test
    @DisplayName("서로 다른 token의 동일 identity 동시 소비도 하나만 가입 성공한다")
    void differentTokensSameIdentity_allowsOnlyOneSignup() throws Exception {
        String firstToken = issueSession();
        String secondToken = issueSession();

        List<AttemptResult> results =
                runConcurrently(
                        () -> consume(firstToken, socialUser(3)),
                        () -> consume(secondToken, socialUser(4)));

        assertThat(results)
                .containsExactlyInAnyOrder(AttemptResult.SUCCESS, AttemptResult.REJECTED);
        assertSingleSignupCommitted();
        assertThat(
                        List.of(
                                findSession(firstToken).getStatus(),
                                findSession(secondToken).getStatus()))
                .containsExactlyInAnyOrder(
                        SocialSignupSessionStatus.CONSUMED, SocialSignupSessionStatus.CANCELLED);
    }

    @Test
    @DisplayName("동일 identity 소비 중 발급된 새 세션은 남더라도 이후 가입에 사용할 수 없다")
    void issueDuringConsumption_pendingSessionCannotCreateSecondAccount() throws Exception {
        String consumingToken = issueSession();
        CountDownLatch identityLocked = new CountDownLatch(1);
        CountDownLatch allowConsumption = new CountDownLatch(1);

        Future<AttemptResult> consumption =
                executorService.submit(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .execute(
                                                status -> {
                                                    signupSessionRepository
                                                            .findAllByIdentityAndStatusForUpdate(
                                                                    SocialProvider.KAKAO,
                                                                    identifier.hash(),
                                                                    SocialSignupSessionStatus
                                                                            .PENDING);
                                                    identityLocked.countDown();
                                                    await(allowConsumption);
                                                    return consume(consumingToken, socialUser(5));
                                                }));
        assertThat(identityLocked.await(5, TimeUnit.SECONDS)).isTrue();

        CountDownLatch issueStarted = new CountDownLatch(1);
        Future<String> issuedDuringConsumption =
                executorService.submit(
                        () -> {
                            issueStarted.countDown();
                            return issueSession();
                        });
        assertThat(issueStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);
        allowConsumption.countDown();

        assertThat(consumption.get(10, TimeUnit.SECONDS)).isEqualTo(AttemptResult.SUCCESS);
        String lateToken = issuedDuringConsumption.get(10, TimeUnit.SECONDS);
        assertThat(findSession(lateToken).getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);

        User rejectedUser = socialUser(6);
        assertThatThrownBy(
                        () ->
                                signupTransactionService.createAccount(
                                        rejectedUser,
                                        lateToken,
                                        rejectedUser.getPhoneNumber(),
                                        rejectedUser.getNickname()))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(ErrorCode.ALREADY_REGISTERED));
        assertSingleSignupCommitted();
    }

    @Test
    @DisplayName("identity 잠금 timeout은 가입 성공으로 처리되지 않고 전체 상태를 보존한다")
    void identityLockTimeout_rejectsSignupWithoutOrphans() throws Exception {
        String token = issueSession();
        CountDownLatch identityLocked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Future<?> lockHolder =
                executorService.submit(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    signupSessionRepository
                                                            .findAllByIdentityAndStatusForUpdate(
                                                                    SocialProvider.KAKAO,
                                                                    identifier.hash(),
                                                                    SocialSignupSessionStatus
                                                                            .PENDING);
                                                    identityLocked.countDown();
                                                    await(releaseLock);
                                                }));
        assertThat(identityLocked.await(5, TimeUnit.SECONDS)).isTrue();

        Future<AttemptResult> timedOut =
                executorService.submit(() -> consumeWithOneSecondLockTimeout(token, socialUser(7)));

        assertThat(timedOut.get(10, TimeUnit.SECONDS)).isEqualTo(AttemptResult.LOCK_TIMEOUT);
        releaseLock.countDown();
        lockHolder.get(5, TimeUnit.SECONDS);

        assertThat(userRepository.count()).isEqualTo(userCountBefore);
        assertThat(socialAccountRepository.count()).isEqualTo(socialAccountCountBefore);
        assertThat(refreshTokenRepository.count()).isEqualTo(refreshTokenCountBefore);
        assertThat(findSession(token).getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);
    }

    @Test
    @DisplayName("identity 교차 잠금 deadlock에서도 두 가입이 모두 성공하지 않는다")
    void identityDeadlock_allowsAtMostOneSignup() throws Exception {
        String secondProviderUserId = "signup-deadlock-" + SEQUENCE.incrementAndGet();
        RejoinBlockIdentifier secondIdentifier = identifierHasher.hashKakao(secondProviderUserId);
        trackedIdentifiers.add(secondIdentifier);
        String firstToken = issueSession();
        String secondToken = issueSession(secondProviderUserId, secondIdentifier);
        CountDownLatch firstLocksAcquired = new CountDownLatch(2);

        Future<AttemptResult> first =
                submitCrossIdentityConsumption(
                        identifier, secondToken, socialUser(8), firstLocksAcquired);
        Future<AttemptResult> second =
                submitCrossIdentityConsumption(
                        secondIdentifier, firstToken, socialUser(9), firstLocksAcquired);

        assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(AttemptResult.SUCCESS, AttemptResult.DEADLOCK);
        assertThat(userRepository.count()).isEqualTo(userCountBefore + 1);
        assertThat(socialAccountRepository.count()).isEqualTo(socialAccountCountBefore + 1);
        assertThat(refreshTokenRepository.count()).isEqualTo(refreshTokenCountBefore + 1);
        assertThat(
                        trackedIdentifiers.stream()
                                .map(
                                        tracked ->
                                                socialAccountRepository
                                                        .findByProviderAndProviderUserKey(
                                                                SocialProvider.KAKAO,
                                                                tracked.hash()))
                                .filter(java.util.Optional::isPresent))
                .hasSize(1);
    }

    private String issueSession() {
        return issueSession(providerUserId, identifier);
    }

    private String issueSession(
            String sessionProviderUserId, RejoinBlockIdentifier sessionIdentifier) {
        String token =
                signupSessionService.issue(
                        SocialProvider.KAKAO,
                        sessionIdentifier,
                        providerIdCipher.encrypt(sessionProviderUserId));
        issuedTokens.add(token);
        return token;
    }

    private AttemptResult consumeWithOneSecondLockTimeout(String token, User user) {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status -> {
                            Integer originalTimeout =
                                    jdbcTemplate.queryForObject(
                                            "SELECT @@SESSION.innodb_lock_wait_timeout",
                                            Integer.class);
                            jdbcTemplate.execute("SET SESSION innodb_lock_wait_timeout = 1");
                            try {
                                signupTransactionService.createAccount(
                                        user, token, user.getPhoneNumber(), user.getNickname());
                                return AttemptResult.SUCCESS;
                            } catch (RuntimeException exception) {
                                status.setRollbackOnly();
                                return mysqlErrorCode(exception) == 1205
                                        ? AttemptResult.LOCK_TIMEOUT
                                        : AttemptResult.REJECTED;
                            } finally {
                                jdbcTemplate.execute(
                                        "SET SESSION innodb_lock_wait_timeout = "
                                                + originalTimeout);
                            }
                        });
    }

    private Future<AttemptResult> submitCrossIdentityConsumption(
            RejoinBlockIdentifier initiallyLockedIdentity,
            String targetToken,
            User user,
            CountDownLatch locksAcquired) {
        return executorService.submit(
                () -> {
                    try {
                        return new TransactionTemplate(transactionManager)
                                .execute(
                                        status -> {
                                            signupSessionRepository
                                                    .findAllByIdentityAndStatusForUpdate(
                                                            SocialProvider.KAKAO,
                                                            initiallyLockedIdentity.hash(),
                                                            SocialSignupSessionStatus.PENDING);
                                            locksAcquired.countDown();
                                            await(locksAcquired);
                                            signupTransactionService.createAccount(
                                                    user,
                                                    targetToken,
                                                    user.getPhoneNumber(),
                                                    user.getNickname());
                                            return AttemptResult.SUCCESS;
                                        });
                    } catch (RuntimeException exception) {
                        return mysqlErrorCode(exception) == 1213
                                ? AttemptResult.DEADLOCK
                                : AttemptResult.REJECTED;
                    }
                });
    }

    private int mysqlErrorCode(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getErrorCode();
            }
            cause = cause.getCause();
        }
        return 0;
    }

    private AttemptResult consume(String token, User user) {
        try {
            signupTransactionService.createAccount(
                    user, token, user.getPhoneNumber(), user.getNickname());
            return AttemptResult.SUCCESS;
        } catch (RuntimeException exception) {
            return AttemptResult.REJECTED;
        }
    }

    private List<AttemptResult> runConcurrently(
            java.util.concurrent.Callable<AttemptResult> firstCall,
            java.util.concurrent.Callable<AttemptResult> secondCall)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<AttemptResult> first = submit(ready, start, firstCall);
        Future<AttemptResult> second = submit(ready, start, secondCall);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
    }

    private Future<AttemptResult> submit(
            CountDownLatch ready,
            CountDownLatch start,
            java.util.concurrent.Callable<AttemptResult> call) {
        return executorService.submit(
                () -> {
                    ready.countDown();
                    await(start);
                    return call.call();
                });
    }

    private void assertSingleSignupCommitted() {
        assertThat(userRepository.count()).isEqualTo(userCountBefore + 1);
        assertThat(socialAccountRepository.count()).isEqualTo(socialAccountCountBefore + 1);
        assertThat(refreshTokenRepository.count()).isEqualTo(refreshTokenCountBefore + 1);
        assertThat(
                        socialAccountRepository.findByProviderAndProviderUserKey(
                                SocialProvider.KAKAO, identifier.hash()))
                .isPresent();
    }

    private SocialSignupSession findSession(String token) {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status -> {
                            entityManager.clear();
                            return signupSessionRepository
                                    .findByTokenHash(signupTokenService.hashToken(token))
                                    .orElseThrow();
                        });
    }

    private User socialUser(int offset) {
        int suffix = SEQUENCE.incrementAndGet() + offset;
        return User.createSocial(
                "홍길동",
                LocalDate.of(2002, 3, 15),
                Gender.MALE,
                "010" + String.format("%08d", suffix),
                "signup" + suffix,
                null,
                true,
                true,
                false,
                null,
                List.of(PostingCategory.WELFARE));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 신호를 받지 못했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트가 중단되었습니다.", exception);
        }
    }

    private enum AttemptResult {
        SUCCESS,
        REJECTED,
        LOCK_TIMEOUT,
        DEADLOCK
    }
}
