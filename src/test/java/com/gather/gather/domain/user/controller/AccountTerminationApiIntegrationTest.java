package com.gather.gather.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.auth.entity.AccountRejoinBlock;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskStatus;
import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControl;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminApiClient;
import com.gather.gather.domain.auth.kakao.worker.KakaoUnlinkWorker;
import com.gather.gather.domain.auth.repository.AccountRejoinBlockRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkTaskRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifierHasher;
import com.gather.gather.domain.auth.service.TokenProvider;
import com.gather.gather.domain.user.repository.ProfileImageUploadRepository;
import com.gather.gather.global.infra.s3.ObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AccountTerminationApiIntegrationTest.FixedClockConfiguration.class)
@TestPropertySource(
        properties = {
            "kakao.admin.enabled=false",
            "kakao.admin.unlink-worker.enabled=false",
            "gather.scheduling.enabled=false"
        })
class AccountTerminationApiIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-01T14:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
    private static final String ENDPOINT = "/api/v1/users/me";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TokenProvider tokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private KakaoUnlinkTaskRepository taskRepository;
    @Autowired private AccountRejoinBlockRepository blockRepository;
    @Autowired private ProfileImageUploadRepository profileImageUploadRepository;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private KakaoAdminApiClient kakaoAdminApiClient;
    @MockitoBean private KakaoUnlinkWorker kakaoUnlinkWorker;
    @MockitoBean private ObjectStorage objectStorage;

    private final List<Fixture> fixtures = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            jdbcTemplate.update(
                                    "UPDATE kakao_unlink_worker_control SET status = 'ACTIVE', blocked_at = NULL, blocked_reason = NULL, last_http_status = NULL, last_kakao_code = NULL WHERE id = ?",
                                    KakaoUnlinkWorkerControl.SINGLETON_ID);
                            for (Fixture fixture : fixtures) {
                                jdbcTemplate.update(
                                        "DELETE FROM profile_image_upload WHERE user_id = ?",
                                        fixture.userId());
                                jdbcTemplate.update(
                                        "DELETE FROM kakao_unlink_task WHERE social_account_id IN (SELECT id FROM social_account WHERE user_id = ?)",
                                        fixture.userId());
                                jdbcTemplate.update(
                                        "DELETE FROM refresh_token WHERE user_id = ?",
                                        fixture.userId());
                                jdbcTemplate.update(
                                        "DELETE FROM account_rejoin_block WHERE source_user_id = ?",
                                        fixture.userId());
                                jdbcTemplate.update(
                                        "DELETE FROM social_account WHERE user_id = ?",
                                        fixture.userId());
                                jdbcTemplate.update(
                                        "DELETE FROM user_interest_category WHERE user_id = ?",
                                        fixture.userId());
                                jdbcTemplate.update(
                                        "DELETE FROM users WHERE id = ?", fixture.userId());
                                jdbcTemplate.update(
                                        "DELETE FROM account_identity_guard WHERE identity_type = 'PHONE' AND identity_hash = ? AND key_version = ?",
                                        fixture.phoneIdentifier().hash(),
                                        fixture.phoneIdentifier().keyVersion());
                            }
                        });
        fixtures.clear();
    }

    @Test
    void localAccount_returnsCompletedAndDeletesRefreshToken() throws Exception {
        Fixture fixture = createLocalFixture(false);

        performDelete(fixture.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.occurredAt").value("2026-08-01T14:00:00Z"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(
                        header().string(
                                        HttpHeaders.SET_COOKIE,
                                        matchesPattern(clearCookieMatcher())));

        User user = userRepository.findById(fixture.userId()).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(refreshTokenRepository.findById(fixture.refreshTokenId())).isEmpty();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM kakao_unlink_task task JOIN social_account account ON task.social_account_id = account.id WHERE account.user_id = ?",
                                Long.class,
                                fixture.userId()))
                .isZero();
    }

    @Test
    void kakaoAccountWithWorkerDisabled_returnsAcceptedWithoutExternalCalls() throws Exception {
        Fixture fixture = createKakaoFixture();

        performDelete(fixture.accessToken())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.occurredAt").value("2026-08-01T14:00:00Z"))
                .andExpect(
                        header().string(
                                        HttpHeaders.SET_COOKIE,
                                        matchesPattern(clearCookieMatcher())));

        User user = userRepository.findById(fixture.userId()).orElseThrow();
        SocialAccount account =
                socialAccountRepository.findById(fixture.socialAccountId()).orElseThrow();
        KakaoUnlinkTask task =
                taskRepository
                        .findBySocialAccountIdAndGeneration(fixture.socialAccountId(), 1L)
                        .orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(account.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.UNLINK_PENDING);
        assertThat(task.getStatus()).isEqualTo(KakaoUnlinkTaskStatus.PENDING);
        assertThat(refreshTokenRepository.findById(fixture.refreshTokenId())).isEmpty();
        verifyNoInteractions(kakaoAdminApiClient, kakaoUnlinkWorker, objectStorage);
    }

    @Test
    void pendingRepeatDelete_preservesFirstResultTaskAndBlocks() throws Exception {
        Fixture fixture = createKakaoFixture();

        MvcResult first =
                performDelete(fixture.accessToken()).andExpect(status().isAccepted()).andReturn();
        KakaoUnlinkTask before =
                taskRepository
                        .findBySocialAccountIdAndGeneration(fixture.socialAccountId(), 1L)
                        .orElseThrow();
        LocalDateTime taskCreatedAt = before.getCreatedAt();
        int retryCycle = before.getRetryCycle();
        int attemptCount = before.getAttemptCount();
        LocalDateTime phoneBlockExpiration = blockExpiration(fixture.phoneIdentifier());
        LocalDateTime kakaoBlockExpiration = blockExpiration(fixture.kakaoIdentifier());

        MvcResult second =
                performDelete(fixture.accessToken()).andExpect(status().isAccepted()).andReturn();

        assertThat(occurredAt(first)).isEqualTo(occurredAt(second));
        KakaoUnlinkTask after =
                taskRepository
                        .findBySocialAccountIdAndGeneration(fixture.socialAccountId(), 1L)
                        .orElseThrow();
        assertThat(after.getCreatedAt()).isEqualTo(taskCreatedAt);
        assertThat(after.getRetryCycle()).isEqualTo(retryCycle);
        assertThat(after.getAttemptCount()).isEqualTo(attemptCount);
        assertThat(blockExpiration(fixture.phoneIdentifier())).isEqualTo(phoneBlockExpiration);
        assertThat(blockExpiration(fixture.kakaoIdentifier())).isEqualTo(kakaoBlockExpiration);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM kakao_unlink_task WHERE social_account_id = ?",
                                Long.class,
                                fixture.socialAccountId()))
                .isEqualTo(1L);
        assertThat(second.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .matches(clearCookieMatcher());
    }

    @Test
    void withdrawnRepeatDelete_preservesFirstResultAndDeletionTask() throws Exception {
        Fixture fixture = createLocalFixture(true);

        MvcResult first =
                performDelete(fixture.accessToken()).andExpect(status().isOk()).andReturn();
        User before = userRepository.findById(fixture.userId()).orElseThrow();
        LocalDateTime withdrawnAt = before.getWithdrawnAt();
        WithdrawalReason reason = before.getWithdrawalReason();
        long deletionTaskCount = profileImageUploadRepository.count();
        LocalDateTime blockExpiration = blockExpiration(fixture.phoneIdentifier());

        MvcResult second =
                performDelete(fixture.accessToken()).andExpect(status().isOk()).andReturn();

        User after = userRepository.findById(fixture.userId()).orElseThrow();
        assertThat(occurredAt(first)).isEqualTo(occurredAt(second));
        assertThat(after.getWithdrawnAt()).isEqualTo(withdrawnAt);
        assertThat(after.getWithdrawalReason()).isEqualTo(reason);
        assertThat(after.getAnonymizedAt()).isEqualTo(NOW);
        assertThat(profileImageUploadRepository.count()).isEqualTo(deletionTaskCount);
        assertThat(blockExpiration(fixture.phoneIdentifier())).isEqualTo(blockExpiration);
        assertThat(second.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .matches(clearCookieMatcher());
    }

    @Test
    void inconsistentPendingState_returns409WithoutMutationOrSuccessCookie() throws Exception {
        Fixture fixture = createKakaoFixture();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            User user = userRepository.findById(fixture.userId()).orElseThrow();
                            user.requestWithdrawal(WithdrawalReason.SELF, NOW);
                        });

        performDelete(fixture.accessToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_TERMINATION_STATE_CONFLICT"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        SocialAccount account =
                socialAccountRepository.findById(fixture.socialAccountId()).orElseThrow();
        assertThat(account.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.LINKED);
        assertThat(taskRepository.findBySocialAccountIdAndGeneration(fixture.socialAccountId(), 1L))
                .isEmpty();
        assertThat(refreshTokenRepository.findById(fixture.refreshTokenId())).isPresent();
    }

    @Test
    void configurationBlocked_stillAcceptsPendingTaskAndKeepsControlBlocked() throws Exception {
        Fixture fixture = createKakaoFixture();
        jdbcTemplate.update(
                "UPDATE kakao_unlink_worker_control SET status = 'CONFIGURATION_BLOCKED', blocked_at = ?, blocked_reason = 'CONFIGURATION', last_http_status = 401, last_kakao_code = -401 WHERE id = ?",
                NOW,
                KakaoUnlinkWorkerControl.SINGLETON_ID);

        performDelete(fixture.accessToken())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM kakao_unlink_worker_control WHERE id = ?",
                                String.class,
                                KakaoUnlinkWorkerControl.SINGLETON_ID))
                .isEqualTo("CONFIGURATION_BLOCKED");
        assertThat(
                        taskRepository
                                .findBySocialAccountIdAndGeneration(fixture.socialAccountId(), 1L)
                                .orElseThrow()
                                .getStatus())
                .isEqualTo(KakaoUnlinkTaskStatus.PENDING);
        assertThat(userRepository.findById(fixture.userId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWAL_PENDING);
    }

    @Test
    void openApiDocumentsBodylessAuthenticated200And202Contract() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();

        JsonNode openApi = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode operation = openApi.path("paths").path(ENDPOINT).path("delete");
        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.has("requestBody")).isFalse();
        assertThat(operation.path("responses").has("200")).isTrue();
        assertThat(operation.path("responses").has("202")).isTrue();
        assertThat(operation.path("responses").has("401")).isTrue();
        assertThat(operation.path("responses").has("409")).isTrue();
        assertThat(operation.path("responses").has("500")).isTrue();
        assertThat(openApi.path("security").toString()).contains("bearerAuth");
    }

    private org.springframework.test.web.servlet.ResultActions performDelete(String accessToken)
            throws Exception {
        return mockMvc.perform(
                delete(ENDPOINT)
                        .servletPath(ENDPOINT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private Fixture createLocalFixture(boolean withProfileImage) {
        return createFixture(false, withProfileImage);
    }

    private Fixture createKakaoFixture() {
        return createFixture(true, false);
    }

    private Fixture createFixture(boolean kakao, boolean withProfileImage) {
        Fixture fixture =
                new TransactionTemplate(transactionManager)
                        .execute(
                                status -> {
                                    String suffix = UUID.randomUUID().toString().replace("-", "");
                                    String phone =
                                            "010"
                                                    + "%08d"
                                                            .formatted(
                                                                    Math.floorMod(
                                                                            suffix.hashCode(),
                                                                            100_000_000));
                                    User user =
                                            kakao
                                                    ? User.createSocial(
                                                            "api-test",
                                                            null,
                                                            null,
                                                            phone,
                                                            "k" + suffix.substring(0, 20),
                                                            null,
                                                            true,
                                                            true,
                                                            false,
                                                            null,
                                                            List.of())
                                                    : User.create(
                                                            "api-test",
                                                            null,
                                                            null,
                                                            phone,
                                                            suffix + "@example.com",
                                                            "encoded-password",
                                                            "l" + suffix.substring(0, 20),
                                                            null,
                                                            true,
                                                            true,
                                                            false,
                                                            null,
                                                            List.of());
                                    user = userRepository.saveAndFlush(user);
                                    if (withProfileImage) {
                                        user.changeProfileImageKey(
                                                "profiles/" + user.getId() + "/current.jpg");
                                    }
                                    SocialAccount socialAccount = null;
                                    RejoinBlockIdentifier kakaoIdentifier = null;
                                    if (kakao) {
                                        String providerKey = suffix + suffix;
                                        kakaoIdentifier =
                                                new RejoinBlockIdentifier(
                                                        AccountRejoinBlockIdentifierType.KAKAO,
                                                        providerKey,
                                                        1);
                                        socialAccount =
                                                socialAccountRepository.saveAndFlush(
                                                        SocialAccount.createLinked(
                                                                user,
                                                                SocialProvider.KAKAO,
                                                                suffix,
                                                                providerKey,
                                                                1,
                                                                new EncryptedProviderUserId(
                                                                        "ciphertext-" + suffix, 1),
                                                                NOW.minusDays(1)));
                                    }
                                    RefreshToken refreshToken =
                                            refreshTokenRepository.saveAndFlush(
                                                    RefreshToken.create(
                                                            suffix + suffix,
                                                            user,
                                                            NOW.plusDays(1)));
                                    return new Fixture(
                                            user.getId(),
                                            socialAccount == null ? null : socialAccount.getId(),
                                            refreshToken.getId(),
                                            tokenProvider.createAccessToken(user),
                                            identifierHasher.hashPhone(phone),
                                            kakaoIdentifier);
                                });
        fixtures.add(fixture);
        return fixture;
    }

    private LocalDateTime blockExpiration(RejoinBlockIdentifier identifier) {
        AccountRejoinBlock block =
                blockRepository
                        .findByIdentifierTypeAndIdentifierHash(identifier.type(), identifier.hash())
                        .orElseThrow();
        return block.getExpiresAt();
    }

    private String occurredAt(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return body.path("data").path("occurredAt").asText();
    }

    private String clearCookieMatcher() {
        return "(?=.*gather_refresh_token=)(?=.*Max-Age=0)(?=.*Path=/api/v1/auth)(?=.*HttpOnly)(?=.*SameSite=Lax).*";
    }

    private record Fixture(
            Long userId,
            Long socialAccountId,
            Long refreshTokenId,
            String accessToken,
            RejoinBlockIdentifier phoneIdentifier,
            RejoinBlockIdentifier kakaoIdentifier) {}

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock accountTerminationApiTestClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}
