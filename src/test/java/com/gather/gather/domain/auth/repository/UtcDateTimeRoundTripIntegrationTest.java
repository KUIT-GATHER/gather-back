package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.AccountRejoinBlock;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.SocialSignupSession;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UtcDateTimeRoundTripIntegrationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-31T05:25:56.123456Z"), ZoneOffset.UTC);
    private static final LocalDateTime CAPTURED_NOW = LocalDateTime.now(FIXED_CLOCK);
    private static final String PROVIDER_USER_KEY = "a".repeat(64);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private SocialSignupSessionRepository socialSignupSessionRepository;
    @Autowired private AccountRejoinBlockRepository accountRejoinBlockRepository;
    @Autowired private KakaoUnlinkTaskRepository kakaoUnlinkTaskRepository;

    @Test
    void jdbcSessionAndDatetimeRoundTripUseUtcWithoutNineHourShift() {
        Map<String, Object> timeZoneState =
                jdbcTemplate.queryForMap(
                        """
                        select
                            @@session.time_zone as session_timezone,
                            @@global.time_zone as global_timezone,
                            @@system_time_zone as system_timezone,
                            now(6) as session_now,
                            utc_timestamp(6) as utc_now,
                            timestampdiff(microsecond, utc_timestamp(6), now(6)) as offset_micros
                        """);

        assertThat(Math.abs(((Number) timeZoneState.get("offset_micros")).longValue()))
                .as(
                        "session timezone=%s, global timezone=%s, system timezone=%s",
                        timeZoneState.get("session_timezone"),
                        timeZoneState.get("global_timezone"),
                        timeZoneState.get("system_timezone"))
                .isLessThan(1_000_000L);

        User user =
                userRepository.save(
                        User.createSocial(
                                "UTC test",
                                null,
                                null,
                                "01099990001",
                                "utc-round-trip",
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
                                "utc-round-trip-provider",
                                PROVIDER_USER_KEY,
                                1,
                                new EncryptedProviderUserId("ciphertext", 1),
                                CAPTURED_NOW.minusMinutes(1)));
        socialAccount.markUnlinkPending(CAPTURED_NOW);
        user.withdraw(WithdrawalReason.SELF, CAPTURED_NOW);

        SocialSignupSession signupSession =
                socialSignupSessionRepository.save(
                        SocialSignupSession.createKakao(
                                "b".repeat(64),
                                new RejoinBlockIdentifier(
                                        AccountRejoinBlockIdentifierType.KAKAO,
                                        PROVIDER_USER_KEY,
                                        1),
                                new EncryptedProviderUserId("session-ciphertext", 1),
                                CAPTURED_NOW.plusMinutes(15),
                                CAPTURED_NOW.minusMinutes(1)));
        signupSession.cancel(CAPTURED_NOW);

        AccountRejoinBlock rejoinBlock =
                accountRejoinBlockRepository.save(
                        AccountRejoinBlock.create(
                                AccountRejoinBlockIdentifierType.PHONE,
                                "c".repeat(64),
                                1,
                                CAPTURED_NOW.plusDays(7),
                                user.getId(),
                                CAPTURED_NOW));
        KakaoUnlinkTask unlinkTask =
                kakaoUnlinkTaskRepository.save(
                        KakaoUnlinkTask.pending(
                                socialAccount, socialAccount.getGeneration(), CAPTURED_NOW));

        entityManager.flush();
        Long userId = user.getId();
        Long socialAccountId = socialAccount.getId();
        Long signupSessionId = signupSession.getId();
        Long rejoinBlockId = rejoinBlock.getId();
        Long unlinkTaskId = unlinkTask.getId();
        entityManager.clear();

        assertThat(userRepository.findById(userId).orElseThrow().getWithdrawnAt())
                .isEqualTo(CAPTURED_NOW);
        assertThat(socialAccountRepository.findById(socialAccountId).orElseThrow().getUpdatedAt())
                .isEqualTo(CAPTURED_NOW);
        assertThat(
                        socialSignupSessionRepository
                                .findById(signupSessionId)
                                .orElseThrow()
                                .getCancelledAt())
                .isEqualTo(CAPTURED_NOW);
        assertThat(
                        accountRejoinBlockRepository
                                .findById(rejoinBlockId)
                                .orElseThrow()
                                .getExpiresAt())
                .isEqualTo(CAPTURED_NOW.plusDays(7));

        KakaoUnlinkTask reloadedTask =
                kakaoUnlinkTaskRepository.findById(unlinkTaskId).orElseThrow();
        assertThat(reloadedTask.getNextAttemptAt()).isEqualTo(CAPTURED_NOW);
        assertThat(reloadedTask.getCreatedAt()).isEqualTo(CAPTURED_NOW);
        assertThat(ChronoUnit.HOURS.between(CAPTURED_NOW, reloadedTask.getNextAttemptAt()))
                .isZero();
    }
}
