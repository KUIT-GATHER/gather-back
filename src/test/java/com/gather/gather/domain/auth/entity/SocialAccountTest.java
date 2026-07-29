package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SocialAccountTest {

    private static final String PROVIDER_USER_KEY = "a".repeat(64);
    private static final EncryptedProviderUserId ENCRYPTED_PROVIDER_USER_ID =
            new EncryptedProviderUserId("ciphertext", 1);
    private static final LocalDateTime CONNECTED_AT = LocalDateTime.of(2026, 7, 29, 12, 0);

    @Test
    @DisplayName("최초 연결은 LINKED와 generation 1 및 연결 시각을 저장한다")
    void createLinked_initializesLifecycle() {
        User user = persistedUser(1L);

        SocialAccount account = createLinked(user);

        assertThat(account.getUser()).isSameAs(user);
        assertThat(account.getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(account.getProviderUserKey()).isEqualTo(PROVIDER_USER_KEY);
        assertThat(account.getProviderUserKeyVersion()).isEqualTo(1);
        assertThat(account.getProviderUserIdCiphertext()).isEqualTo("ciphertext");
        assertThat(account.getEncryptionKeyVersion()).isEqualTo(1);
        assertThat(account.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.LINKED);
        assertThat(account.getGeneration()).isEqualTo(1L);
        assertThat(account.getConnectedAt()).isEqualTo(CONNECTED_AT);
        assertThat(account.getUnlinkedAt()).isNull();
        assertThat(account.isLinked()).isTrue();
        assertThat(account.matchesGeneration(1)).isTrue();
    }

    @Test
    @DisplayName("연결 해제 상태 전이는 generation을 유지하고 해제 시각을 저장한다")
    void unlinkTransitions_preserveGeneration() {
        SocialAccount account = createLinked(persistedUser(1L));
        LocalDateTime pendingAt = CONNECTED_AT.plusMinutes(1);
        LocalDateTime unlinkedAt = CONNECTED_AT.plusMinutes(2);

        account.markUnlinkPending(pendingAt);
        assertThat(account.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.UNLINK_PENDING);
        assertThat(account.getGeneration()).isEqualTo(1L);
        assertThat(account.getUnlinkedAt()).isNull();

        account.markUnlinked(unlinkedAt);
        assertThat(account.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.UNLINKED);
        assertThat(account.getGeneration()).isEqualTo(1L);
        assertThat(account.getUnlinkedAt()).isEqualTo(unlinkedAt);
    }

    @Test
    @DisplayName("잘못된 순서와 중복 상태 전이는 조용히 허용하지 않는다")
    void invalidAndDuplicateTransitions_areRejected() {
        SocialAccount account = createLinked(persistedUser(1L));

        assertThatThrownBy(() -> account.markUnlinked(CONNECTED_AT.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);

        account.markUnlinkPending(CONNECTED_AT.plusMinutes(1));
        assertThatThrownBy(() -> account.markUnlinkPending(CONNECTED_AT.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);

        account.markUnlinked(CONNECTED_AT.plusMinutes(3));
        assertThatThrownBy(() -> account.markUnlinked(CONNECTED_AT.plusMinutes(4)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("UNLINKED 계정을 새 User에 relink할 때만 generation이 증가한다")
    void relink_toNewUser_incrementsGeneration() {
        User originalUser = persistedUser(1L);
        User newUser = persistedUser(2L);
        SocialAccount account = createLinked(originalUser);
        account.markUnlinkPending(CONNECTED_AT.plusMinutes(1));
        account.markUnlinked(CONNECTED_AT.plusMinutes(2));
        LocalDateTime reconnectedAt = CONNECTED_AT.plusDays(1);

        account.relink(newUser, new EncryptedProviderUserId("new-ciphertext", 2), reconnectedAt);

        assertThat(account.getUser()).isSameAs(newUser);
        assertThat(account.getGeneration()).isEqualTo(2L);
        assertThat(account.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.LINKED);
        assertThat(account.getConnectedAt()).isEqualTo(reconnectedAt);
        assertThat(account.getUnlinkedAt()).isNull();
        assertThat(account.getProviderUserIdCiphertext()).isEqualTo("new-ciphertext");
        assertThat(account.getEncryptionKeyVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("동일 User 재연결과 LINKED 상태 재연결은 거부한다")
    void relink_withoutNewUnlinkedUser_isRejected() {
        User user = persistedUser(1L);
        SocialAccount account = createLinked(user);

        assertThatThrownBy(
                        () ->
                                account.relink(
                                        persistedUser(2L),
                                        ENCRYPTED_PROVIDER_USER_ID,
                                        CONNECTED_AT.plusDays(1)))
                .isInstanceOf(IllegalStateException.class);

        account.markUnlinkPending(CONNECTED_AT.plusMinutes(1));
        account.markUnlinked(CONNECTED_AT.plusMinutes(2));

        assertThatThrownBy(
                        () ->
                                account.relink(
                                        user, ENCRYPTED_PROVIDER_USER_ID, CONNECTED_AT.plusDays(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("기존 평문 row는 HMAC·암호문·LINKED 생명주기로 한 번만 backfill한다")
    void backfillLegacyIdentity_initializesLifecycleOnce() {
        SocialAccount legacy = newLegacyAccount(persistedUser(1L));
        LocalDateTime backfilledAt = CONNECTED_AT.plusDays(1);

        legacy.backfillLegacyIdentity(
                PROVIDER_USER_KEY, 1, ENCRYPTED_PROVIDER_USER_ID, backfilledAt);

        assertThat(legacy.getProviderUserKey()).isEqualTo(PROVIDER_USER_KEY);
        assertThat(legacy.getGeneration()).isEqualTo(1L);
        assertThat(legacy.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.LINKED);
        assertThat(legacy.getConnectedAt()).isEqualTo(CONNECTED_AT);
        assertThat(legacy.getUpdatedAt()).isEqualTo(backfilledAt);
        assertThatThrownBy(
                        () ->
                                legacy.backfillLegacyIdentity(
                                        PROVIDER_USER_KEY,
                                        1,
                                        ENCRYPTED_PROVIDER_USER_ID,
                                        backfilledAt.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    private SocialAccount createLinked(User user) {
        return SocialAccount.createLinked(
                user,
                SocialProvider.KAKAO,
                "123456789",
                PROVIDER_USER_KEY,
                1,
                ENCRYPTED_PROVIDER_USER_ID,
                CONNECTED_AT);
    }

    private SocialAccount newLegacyAccount(User user) {
        SocialAccount account = new SocialAccount();
        ReflectionTestUtils.setField(account, "user", user);
        ReflectionTestUtils.setField(account, "provider", SocialProvider.KAKAO);
        ReflectionTestUtils.setField(account, "legacyProviderUserId", "123456789");
        ReflectionTestUtils.setField(account, "createdAt", CONNECTED_AT);
        ReflectionTestUtils.setField(account, "updatedAt", CONNECTED_AT);
        return account;
    }

    private User persistedUser(long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
