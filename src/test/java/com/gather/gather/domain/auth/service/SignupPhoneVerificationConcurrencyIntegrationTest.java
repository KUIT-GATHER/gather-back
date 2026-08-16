package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.PhoneVerificationStatus;
import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.entity.EmailVerification;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PhoneVerification;
import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import com.gather.gather.domain.auth.kakao.dto.KakaoSignupRequest;
import com.gather.gather.domain.auth.kakao.service.KakaoAuthService;
import com.gather.gather.domain.auth.kakao.service.SocialSignupSessionService;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenService;
import com.gather.gather.domain.auth.octomo.client.OctomoApiClient;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class SignupPhoneVerificationConcurrencyIntegrationTest {

    private static final String PHONE_NUMBER = "01095550005";
    private static final String EMAIL_PREFIX = "phone-verification-race-";

    @Autowired private AuthService authService;
    @Autowired private KakaoAuthService kakaoAuthService;
    @Autowired private PhoneVerificationService phoneVerificationService;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private PhoneVerificationRepository phoneVerificationRepository;
    @Autowired private SocialSignupSessionRepository signupSessionRepository;
    @Autowired private SocialSignupSessionService signupSessionService;
    @Autowired private SocialSignupTokenService signupTokenService;
    @Autowired private RegionRepository regionRepository;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private SocialAccountProviderIdCipher providerIdCipher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;
    @MockitoBean private OctomoApiClient octomoApiClient;

    private final List<String> signupTokens = new ArrayList<>();
    private ExecutorService executorService;
    private UUID verificationId;
    private Long activityRegionId;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(2);
        verificationId = UUID.randomUUID();
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            Region region =
                                    regionRepository.save(
                                            Region.create(
                                                    "phone-verification-race",
                                                    2,
                                                    "phone-verification-race-code",
                                                    null));
                            activityRegionId = region.getId();
                        });
    }

    @AfterEach
    void cleanUp() throws Exception {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        RejoinBlockIdentifier phoneIdentifier = identifierHasher.hashPhone(PHONE_NUMBER);
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            jdbcTemplate.update(
                                    "delete from refresh_token where user_id in (select id from users where phone_number = ?)",
                                    PHONE_NUMBER);
                            jdbcTemplate.update(
                                    "delete from social_account where user_id in (select id from users where phone_number = ?)",
                                    PHONE_NUMBER);
                            jdbcTemplate.update(
                                    "delete from user_interest_category where user_id in (select id from users where phone_number = ?)",
                                    PHONE_NUMBER);
                            jdbcTemplate.update(
                                    "delete from users where phone_number = ?", PHONE_NUMBER);
                            jdbcTemplate.update(
                                    "delete from email_verification where email like ?",
                                    EMAIL_PREFIX + "%");
                            for (String signupToken : signupTokens) {
                                signupSessionRepository
                                        .findByTokenHash(
                                                signupTokenService.validateAndHash(signupToken))
                                        .ifPresent(signupSessionRepository::delete);
                            }
                            phoneVerificationRepository
                                    .findByVerificationId(verificationId.toString())
                                    .ifPresent(phoneVerificationRepository::delete);
                            jdbcTemplate.update(
                                    "delete from account_identity_guard where identity_type = 'PHONE' and key_version = ? and identity_hash = ?",
                                    phoneIdentifier.keyVersion(),
                                    phoneIdentifier.hash());
                            regionRepository.deleteById(activityRegionId);
                        });
        signupTokens.clear();
    }

    @Test
    @DisplayName("일반 가입 두 건이 같은 인증 ID를 사용하면 정확히 하나만 성공한다")
    void emailSignupVsEmailSignup_allowsExactlyOneSuccess() throws Exception {
        prepareVerification(true);
        prepareVerifiedEmail(EMAIL_PREFIX + "first@example.com");
        prepareVerifiedEmail(EMAIL_PREFIX + "second@example.com");

        List<SignupOutcome> outcomes =
                runConcurrently(
                        () ->
                                authService.signup(
                                        emailSignupRequest(
                                                EMAIL_PREFIX + "first@example.com", "일반경쟁가")),
                        () ->
                                authService.signup(
                                        emailSignupRequest(
                                                EMAIL_PREFIX + "second@example.com", "일반경쟁나")));

        assertSingleConsumption(outcomes);
    }

    @Test
    @DisplayName("카카오 가입 두 건이 같은 인증 ID를 사용하면 정확히 하나만 성공한다")
    void kakaoSignupVsKakaoSignup_allowsExactlyOneSuccess() throws Exception {
        prepareVerification(true);
        String firstToken = issueSignupToken("phone-race-kakao-first");
        String secondToken = issueSignupToken("phone-race-kakao-second");

        List<SignupOutcome> outcomes =
                runConcurrently(
                        () -> kakaoAuthService.signup(firstToken, kakaoSignupRequest("카카오경쟁가")),
                        () -> kakaoAuthService.signup(secondToken, kakaoSignupRequest("카카오경쟁나")));

        assertSingleConsumption(outcomes);
    }

    @Test
    @DisplayName("일반 가입과 카카오 가입이 같은 인증 ID를 사용하면 정확히 하나만 성공한다")
    void emailSignupVsKakaoSignup_allowsExactlyOneSuccess() throws Exception {
        prepareVerification(true);
        String email = EMAIL_PREFIX + "mixed@example.com";
        prepareVerifiedEmail(email);
        String signupToken = issueSignupToken("phone-race-mixed-kakao");

        List<SignupOutcome> outcomes =
                runConcurrently(
                        () -> authService.signup(emailSignupRequest(email, "혼합경쟁가")),
                        () -> kakaoAuthService.signup(signupToken, kakaoSignupRequest("혼합경쟁나")));

        assertSingleConsumption(outcomes);
    }

    @Test
    @DisplayName("confirm과 일반 가입 경쟁 후 User와 인증 소비 상태는 일관된다")
    void confirmVsEmailSignup_keepsUserAndVerificationConsistent() throws Exception {
        prepareVerification(false);
        String email = EMAIL_PREFIX + "confirm@example.com";
        prepareVerifiedEmail(email);
        when(octomoApiClient.existsMessage(anyString(), anyString(), anyInt())).thenReturn(true);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<PhoneVerificationStatus> confirm =
                executorService.submit(
                        () -> {
                            awaitStart(ready, start);
                            return phoneVerificationService
                                    .confirm(verificationId.toString())
                                    .status();
                        });
        Future<SignupOutcome> signup =
                executorService.submit(
                        () -> {
                            awaitStart(ready, start);
                            return attemptSignup(
                                    () -> authService.signup(emailSignupRequest(email, "확인경쟁가입")));
                        });
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(confirm.get(10, TimeUnit.SECONDS)).isEqualTo(PhoneVerificationStatus.VERIFIED);
        SignupOutcome signupOutcome = signup.get(10, TimeUnit.SECONDS);
        PhoneVerification verification =
                phoneVerificationRepository
                        .findByVerificationId(verificationId.toString())
                        .orElseThrow();
        long userCount = userCount();

        assertThat(verification.isVerified()).isTrue();
        assertThat(userCount).isIn(0L, 1L);
        assertThat(verification.getConsumedAt() != null).isEqualTo(userCount == 1L);
        assertThat(signupOutcome.success()).isEqualTo(userCount == 1L);
        if (!signupOutcome.success()) {
            assertThat(signupOutcome.errorCode()).isEqualTo(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        }
    }

    private void prepareVerification(boolean verified) {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            PhoneVerification verification =
                                    PhoneVerification.create(
                                            verificationId.toString(),
                                            PHONE_NUMBER,
                                            PhoneVerificationPurpose.SIGNUP,
                                            "GATHER-FULLRACE1",
                                            now.plusMinutes(5),
                                            now.minusMinutes(1));
                            if (verified) {
                                verification.verify(now.minusMinutes(1));
                            }
                            phoneVerificationRepository.save(verification);
                        });
    }

    private void prepareVerifiedEmail(String email) {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            EmailVerification verification =
                                    EmailVerification.create(email, "123456", now.plusDays(1));
                            verification.verify(now);
                            emailVerificationRepository.save(verification);
                        });
    }

    private String issueSignupToken(String providerUserId) {
        RejoinBlockIdentifier identifier = identifierHasher.hashKakao(providerUserId);
        String token =
                signupSessionService.issue(identifier, providerIdCipher.encrypt(providerUserId));
        signupTokens.add(token);
        return token;
    }

    private SignupRequest emailSignupRequest(String email, String nickname) {
        return new SignupRequest(
                "일반가입",
                LocalDate.of(2001, 1, 1),
                Gender.FEMALE,
                PHONE_NUMBER,
                verificationId,
                email,
                "password1",
                "password1",
                nickname,
                null,
                activityRegionId,
                List.of(PostingCategory.WELFARE),
                true,
                true,
                false);
    }

    private KakaoSignupRequest kakaoSignupRequest(String nickname) {
        return new KakaoSignupRequest(
                "카카오가입",
                LocalDate.of(2002, 3, 15),
                Gender.MALE,
                PHONE_NUMBER,
                verificationId,
                nickname,
                null,
                activityRegionId,
                List.of(PostingCategory.WELFARE),
                true,
                true,
                false);
    }

    private List<SignupOutcome> runConcurrently(SignupAction first, SignupAction second)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<SignupOutcome> firstResult =
                executorService.submit(() -> attemptAfterBarrier(first, ready, start));
        Future<SignupOutcome> secondResult =
                executorService.submit(() -> attemptAfterBarrier(second, ready, start));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        return List.of(
                firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
    }

    private SignupOutcome attemptAfterBarrier(
            SignupAction action, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        awaitStart(ready, start);
        return attemptSignup(action);
    }

    private SignupOutcome attemptSignup(SignupAction action) {
        try {
            action.run();
            return SignupOutcome.succeeded();
        } catch (BusinessException exception) {
            return SignupOutcome.failed(exception.getErrorCode());
        }
    }

    private void assertSingleConsumption(List<SignupOutcome> outcomes) {
        assertThat(outcomes).filteredOn(SignupOutcome::success).hasSize(1);
        assertThat(outcomes)
                .filteredOn(outcome -> !outcome.success())
                .extracting(SignupOutcome::errorCode)
                .containsExactly(ErrorCode.PHONE_VERIFICATION_REQUIRED);
        assertThat(userCount()).isEqualTo(1L);
        assertThat(
                        phoneVerificationRepository
                                .findByVerificationId(verificationId.toString())
                                .orElseThrow()
                                .getConsumedAt())
                .isNotNull();
    }

    private long userCount() {
        Long count =
                jdbcTemplate.queryForObject(
                        "select count(*) from users where phone_number = ?",
                        Long.class,
                        PHONE_NUMBER);
        return count == null ? 0L : count;
    }

    private void awaitStart(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @FunctionalInterface
    private interface SignupAction {
        void run();
    }

    private record SignupOutcome(boolean success, ErrorCode errorCode) {

        private static SignupOutcome succeeded() {
            return new SignupOutcome(true, null);
        }

        private static SignupOutcome failed(ErrorCode errorCode) {
            return new SignupOutcome(false, errorCode);
        }
    }
}
