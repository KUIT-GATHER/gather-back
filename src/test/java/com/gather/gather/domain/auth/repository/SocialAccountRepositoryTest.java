package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SocialAccountRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 12, 0);

    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("provider와 providerUserKey로 User까지 함께 조회한다")
    void findByProviderAndProviderUserKey_returnsAccountWithUser() {
        User user = saveUser("01090000001", "socialrepoone");
        SocialAccount account = saveAccount(user, "legacy-1", "a".repeat(64), 2, "ciphertext-1");
        socialAccountRepository.flush();

        SocialAccount found =
                socialAccountRepository
                        .findByProviderAndProviderUserKey(
                                SocialProvider.KAKAO, account.getProviderUserKey())
                        .orElseThrow();

        assertThat(found.getId()).isEqualTo(account.getId());
        assertThat(found.getProviderUserKeyVersion()).isEqualTo(2);
        assertThat(Hibernate.isInitialized(found.getUser())).isTrue();
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("동일 provider user key는 서로 다른 User에도 하나만 저장된다")
    void save_duplicateProviderUserKey_isRejected() {
        User firstUser = saveUser("01090000002", "socialrepotwo");
        User secondUser = saveUser("01090000003", "socialrepothree");
        String providerUserKey = "b".repeat(64);
        saveAccount(firstUser, "legacy-2", providerUserKey, 1, "ciphertext-2");
        socialAccountRepository.flush();

        assertThatThrownBy(
                        () ->
                                socialAccountRepository.saveAndFlush(
                                        account(
                                                secondUser,
                                                "legacy-3",
                                                providerUserKey,
                                                1,
                                                "ciphertext-3")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("동일 User의 동일 provider 연결은 provider user key가 달라도 하나만 저장된다")
    void save_duplicateUserProvider_isRejected() {
        User user = saveUser("01090000004", "socialrepofour");
        saveAccount(user, "legacy-4", "c".repeat(64), 1, "ciphertext-4");
        socialAccountRepository.flush();

        assertThatThrownBy(
                        () ->
                                socialAccountRepository.saveAndFlush(
                                        account(
                                                user,
                                                "legacy-5",
                                                "d".repeat(64),
                                                1,
                                                "ciphertext-5")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private SocialAccount saveAccount(
            User user,
            String legacyProviderUserId,
            String providerUserKey,
            int providerUserKeyVersion,
            String ciphertext) {
        return socialAccountRepository.save(
                account(
                        user,
                        legacyProviderUserId,
                        providerUserKey,
                        providerUserKeyVersion,
                        ciphertext));
    }

    private SocialAccount account(
            User user,
            String legacyProviderUserId,
            String providerUserKey,
            int providerUserKeyVersion,
            String ciphertext) {
        return SocialAccount.createLinked(
                user,
                SocialProvider.KAKAO,
                legacyProviderUserId,
                providerUserKey,
                providerUserKeyVersion,
                new EncryptedProviderUserId(ciphertext, 1),
                NOW);
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
}
