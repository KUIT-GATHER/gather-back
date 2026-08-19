package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.PasswordResetToken;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.worker.KakaoUnlinkClaim;
import com.gather.gather.domain.auth.kakao.worker.KakaoUnlinkClaimService;
import com.gather.gather.domain.auth.kakao.worker.KakaoUnlinkProcessingResult;
import com.gather.gather.domain.auth.kakao.worker.KakaoUnlinkResultService;
import com.gather.gather.domain.auth.kakao.worker.KakaoUnlinkSingleClaimResult;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 탈퇴 흐름에서 비밀번호 재설정 credential이 남지 않는지 검증한다. */
@SpringBootTest
class PasswordResetWithdrawalIntegrationTest {

    private static final String PHONE_NUMBER = "01096660401";
    private static final String PROVIDER_USER_KEY = "c".repeat(64);

    @Autowired private AccountTerminationService accountTerminationService;
    @Autowired private KakaoUnlinkClaimService kakaoUnlinkClaimService;
    @Autowired private KakaoUnlinkResultService kakaoUnlinkResultService;
    @Autowired private PasswordResetTokenCodec passwordResetTokenCodec;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private SocialAccountProviderIdCipher providerIdCipher;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private Long userId;
    private Long socialAccountId;

    @BeforeEach
    void setUp() {
        userId = null;
        socialAccountId = null;
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            if (userId == null) {
                                return;
                            }
                            passwordResetTokenRepository.deleteAllByUserId(userId);
                            if (socialAccountId != null) {
                                jdbcTemplate.update(
                                        "delete from kakao_unlink_task where social_account_id = ?",
                                        socialAccountId);
                                socialAccountRepository.deleteById(socialAccountId);
                            }
                            jdbcTemplate.update(
                                    "delete from account_rejoin_block where source_user_id = ?",
                                    userId);
                            userRepository.deleteById(userId);
                            jdbcTemplate.update(
                                    "delete from account_identity_guard where identity_hash = ?",
                                    identifierHasher.hashPhone(PHONE_NUMBER).hash());
                        });
    }

    @Test
    @DisplayName("이메일 계정 탈퇴는 재설정 토큰을 함께 파기한다")
    void terminate_localAccount_deletesPasswordResetToken() {
        saveEmailUser();
        saveResetToken();

        accountTerminationService.terminate(userId, WithdrawalReason.SELF);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("이미 탈퇴한 계정에 탈퇴가 재호출되어도 남은 재설정 토큰을 파기한다")
    void terminate_alreadyWithdrawnAccount_stillDeletesStaleToken() {
        saveEmailUser();
        accountTerminationService.terminate(userId, WithdrawalReason.SELF);
        saveResetToken();

        accountTerminationService.terminate(userId, WithdrawalReason.SELF);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    @DisplayName("카카오 탈퇴 접수 시점에도 재설정 토큰을 파기한다")
    void terminate_kakaoAccount_deletesPasswordResetTokenOnAcceptance() {
        saveKakaoUser();
        saveResetToken();

        accountTerminationService.terminate(userId, WithdrawalReason.SELF);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWAL_PENDING);
    }

    @Test
    @DisplayName("카카오 비동기 마무리에서도 남은 재설정 토큰을 방어적으로 파기한다")
    void finalizeLocally_deletesStalePasswordResetToken() {
        saveKakaoUser();
        accountTerminationService.terminate(userId, WithdrawalReason.SELF);
        markSocialAccountUnlinked();
        saveResetToken();

        KakaoUnlinkSingleClaimResult claimResult = kakaoUnlinkClaimService.claimOne(taskId());
        assertThat(claimResult.outcome()).isEqualTo(KakaoUnlinkSingleClaimResult.Outcome.CLAIMED);
        KakaoUnlinkClaim claim = claimResult.claim();

        assertThat(kakaoUnlinkResultService.finalizeLocally(claim))
                .isEqualTo(KakaoUnlinkProcessingResult.SUCCEEDED);

        assertThat(passwordResetTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);
    }

    private Long taskId() {
        return jdbcTemplate.queryForObject(
                "select id from kakao_unlink_task where social_account_id = ?",
                Long.class,
                socialAccountId);
    }

    private void markSocialAccountUnlinked() {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                socialAccountRepository
                                        .findById(socialAccountId)
                                        .orElseThrow()
                                        .markUnlinked(LocalDateTime.now(clock)));
    }

    private void saveResetToken() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            LocalDateTime now = LocalDateTime.now(clock);
                            passwordResetTokenRepository.save(
                                    PasswordResetToken.issue(
                                            userRepository.findById(userId).orElseThrow(),
                                            passwordResetTokenCodec.validateAndHash(
                                                    passwordResetTokenCodec.generateToken()),
                                            now.plusMinutes(10),
                                            now));
                        });
    }

    private void saveEmailUser() {
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                userId =
                                        userRepository
                                                .save(
                                                        User.create(
                                                                "탈퇴회원",
                                                                LocalDate.of(2000, 1, 1),
                                                                Gender.FEMALE,
                                                                PHONE_NUMBER,
                                                                "reset-withdrawal@example.com",
                                                                "encoded-password",
                                                                "재설정탈퇴",
                                                                null,
                                                                true,
                                                                true,
                                                                false,
                                                                null,
                                                                List.of()))
                                                .getId());
    }

    private void saveKakaoUser() {
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            User user =
                                    userRepository.save(
                                            User.createSocial(
                                                    "카카오탈퇴",
                                                    LocalDate.of(2000, 1, 1),
                                                    Gender.MALE,
                                                    PHONE_NUMBER,
                                                    "재설정카카오탈퇴",
                                                    null,
                                                    true,
                                                    true,
                                                    false,
                                                    null,
                                                    List.of()));
                            userId = user.getId();
                            socialAccountId =
                                    socialAccountRepository
                                            .save(
                                                    SocialAccount.createLinked(
                                                            user,
                                                            SocialProvider.KAKAO,
                                                            "legacy-reset-withdrawal",
                                                            PROVIDER_USER_KEY,
                                                            1,
                                                            providerIdCipher.encrypt("987654321"),
                                                            LocalDateTime.now(clock)))
                                            .getId();
                        });
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
