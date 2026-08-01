package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class KakaoUnlinkTaskRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 14, 30);

    @Autowired private KakaoUnlinkTaskRepository taskRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long socialAccountId;
    private Long firstUserId;
    private Long secondUserId;

    @AfterEach
    void cleanUp() {
        if (socialAccountId == null) {
            return;
        }
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            jdbcTemplate.update(
                                    "delete from kakao_unlink_task where social_account_id = ?",
                                    socialAccountId);
                            socialAccountRepository.deleteById(socialAccountId);
                            if (secondUserId != null) {
                                userRepository.deleteById(secondUserId);
                            }
                            userRepository.deleteById(firstUserId);
                        });
    }

    @Test
    void sameSocialAccountAndGenerationCannotBeInsertedTwice() {
        SocialAccount socialAccount = createFixture();
        transactionTemplate()
                .executeWithoutResult(
                        status ->
                                taskRepository.saveAndFlush(
                                        KakaoUnlinkTask.pending(socialAccount, 1L, NOW)));

        assertThatThrownBy(
                        () ->
                                transactionTemplate()
                                        .executeWithoutResult(
                                                status ->
                                                        taskRepository.saveAndFlush(
                                                                KakaoUnlinkTask.pending(
                                                                        socialAccount,
                                                                        1L,
                                                                        NOW.plusMinutes(1)))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void relinkedGenerationAllowsAnotherTaskForTheSameSocialAccount() {
        SocialAccount socialAccount = createFixture();
        transactionTemplate()
                .executeWithoutResult(
                        status -> {
                            taskRepository.saveAndFlush(
                                    KakaoUnlinkTask.pending(socialAccount, 1L, NOW));
                            socialAccount.markUnlinkPending(NOW.plusMinutes(1));
                            socialAccount.markUnlinked(NOW.plusMinutes(2));
                            User secondUser = userRepository.save(socialUser("second"));
                            secondUserId = secondUser.getId();
                            socialAccount.relink(
                                    secondUser,
                                    new EncryptedProviderUserId("ciphertext-generation-2", 1),
                                    NOW.plusMinutes(3));
                            taskRepository.saveAndFlush(
                                    KakaoUnlinkTask.pending(
                                            socialAccount,
                                            socialAccount.getGeneration(),
                                            NOW.plusMinutes(4)));
                        });

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from kakao_unlink_task where social_account_id = ?",
                                Long.class,
                                socialAccountId))
                .isEqualTo(2L);
    }

    private SocialAccount createFixture() {
        return transactionTemplate()
                .execute(
                        status -> {
                            String suffix = UUID.randomUUID().toString().replace("-", "");
                            User user = userRepository.save(socialUser(suffix.substring(0, 8)));
                            firstUserId = user.getId();
                            SocialAccount socialAccount =
                                    socialAccountRepository.save(
                                            SocialAccount.createLinked(
                                                    user,
                                                    SocialProvider.KAKAO,
                                                    suffix,
                                                    "a".repeat(32) + suffix,
                                                    1,
                                                    new EncryptedProviderUserId(
                                                            "ciphertext-" + suffix, 1),
                                                    NOW));
                            socialAccountId = socialAccount.getId();
                            return socialAccount;
                        });
    }

    private User socialUser(String suffix) {
        return User.createSocial(
                "회원",
                null,
                null,
                "010" + Math.abs(suffix.hashCode() % 100_000_000),
                "task" + suffix,
                null,
                true,
                true,
                false,
                null,
                List.of());
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }
}
