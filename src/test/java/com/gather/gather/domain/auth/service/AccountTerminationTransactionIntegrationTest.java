package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.SocialSignupSessionStatus;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.AccountRejoinBlockRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkTaskRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.SocialSignupSessionRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(AccountTerminationTransactionIntegrationTest.FixedClockConfiguration.class)
class AccountTerminationTransactionIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-31T05:25:56.123456Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    @Autowired private AccountTerminationService accountTerminationService;
    @Autowired private UserRepository userRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private SocialSignupSessionRepository socialSignupSessionRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private KakaoUnlinkTaskRepository kakaoUnlinkTaskRepository;
    @Autowired private AccountRejoinBlockRepository accountRejoinBlockRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Fixture fixture;

    @AfterEach
    void cleanUp() {
        if (fixture == null) {
            return;
        }
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            kakaoUnlinkTaskRepository.deleteById(fixture.taskId());
                            socialSignupSessionRepository.deleteById(fixture.sessionId());
                            refreshTokenRepository.deleteById(fixture.refreshTokenId());
                            socialAccountRepository.deleteById(fixture.socialAccountId());
                            accountRejoinBlockRepository.deleteAll();
                            userRepository.deleteById(fixture.userId());
                        });
    }

    @Test
    void duplicateTaskInsertRollsBackEveryTerminationSideEffect() {
        fixture = createFixture();

        assertThatThrownBy(
                        () ->
                                accountTerminationService.terminate(
                                        fixture.userId(), WithdrawalReason.SELF))
                .isInstanceOf(DataIntegrityViolationException.class);

        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            User user = userRepository.findById(fixture.userId()).orElseThrow();
                            SocialAccount socialAccount =
                                    socialAccountRepository
                                            .findById(fixture.socialAccountId())
                                            .orElseThrow();
                            SocialSignupSession signupSession =
                                    socialSignupSessionRepository
                                            .findById(fixture.sessionId())
                                            .orElseThrow();

                            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
                            assertThat(user.getWithdrawalReason()).isNull();
                            assertThat(socialAccount.getLinkStatus())
                                    .isEqualTo(SocialAccountLinkStatus.LINKED);
                            assertThat(signupSession.getStatus())
                                    .isEqualTo(SocialSignupSessionStatus.PENDING);
                            assertThat(signupSession.getCancelledAt()).isNull();
                            assertThat(refreshTokenRepository.findById(fixture.refreshTokenId()))
                                    .isPresent();
                            assertThat(
                                            kakaoUnlinkTaskRepository
                                                    .findBySocialAccountIdAndGeneration(
                                                            fixture.socialAccountId(), 1L))
                                    .isPresent();
                            assertThat(
                                            jdbcTemplate.queryForObject(
                                                    """
                                                    select count(*)
                                                    from account_rejoin_block
                                                    where source_user_id = ?
                                                    """,
                                                    Long.class,
                                                    fixture.userId()))
                                    .isZero();
                        });
    }

    private Fixture createFixture() {
        return transactionTemplate()
                .execute(
                        status -> {
                            String suffix = UUID.randomUUID().toString().replace("-", "");
                            String providerKey = "a".repeat(32) + suffix;
                            String phoneNumber =
                                    "010"
                                            + "%08d"
                                                    .formatted(
                                                            Math.floorMod(
                                                                    suffix.hashCode(),
                                                                    100_000_000));
                            User user =
                                    userRepository.save(
                                            User.createSocial(
                                                    "rollback",
                                                    null,
                                                    null,
                                                    phoneNumber,
                                                    "rollback" + suffix.substring(0, 12),
                                                    null,
                                                    true,
                                                    true,
                                                    false,
                                                    null,
                                                    List.of()));
                            SocialAccount socialAccount =
                                    socialAccountRepository.save(
                                            SocialAccount.createLinked(
                                                    user,
                                                    SocialProvider.KAKAO,
                                                    suffix,
                                                    providerKey,
                                                    1,
                                                    new EncryptedProviderUserId(
                                                            "ciphertext-" + suffix, 1),
                                                    NOW.minusDays(1)));
                            SocialSignupSession signupSession =
                                    socialSignupSessionRepository.save(
                                            SocialSignupSession.createKakao(
                                                    suffix.repeat(2),
                                                    new RejoinBlockIdentifier(
                                                            AccountRejoinBlockIdentifierType.KAKAO,
                                                            providerKey,
                                                            1),
                                                    new EncryptedProviderUserId(
                                                            "session-ciphertext-" + suffix, 1),
                                                    NOW.plusMinutes(15),
                                                    NOW.minusMinutes(1)));
                            RefreshToken refreshToken =
                                    refreshTokenRepository.save(
                                            RefreshToken.create(
                                                    suffix.repeat(2), user, NOW.plusDays(1)));
                            KakaoUnlinkTask existingTask =
                                    kakaoUnlinkTaskRepository.save(
                                            KakaoUnlinkTask.pending(socialAccount, 1L, NOW));

                            return new Fixture(
                                    user.getId(),
                                    socialAccount.getId(),
                                    signupSession.getId(),
                                    refreshToken.getId(),
                                    existingTask.getId());
                        });
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private record Fixture(
            Long userId, Long socialAccountId, Long sessionId, Long refreshTokenId, Long taskId) {}

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock accountTerminationTestClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}
