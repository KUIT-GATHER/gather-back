package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialAccountIdentityServiceTest {

    private static final String PROVIDER_USER_ID = "123456789";
    private static final RejoinBlockIdentifier IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.KAKAO, "a".repeat(64), 2);
    private static final EncryptedProviderUserId ENCRYPTED =
            new EncryptedProviderUserId("ciphertext", 3);

    @Mock private SocialAccountRepository repository;
    @Mock private SocialAccountProviderIdCipher cipher;

    private SocialAccountIdentityService service;

    @BeforeEach
    void setUp() {
        service = new SocialAccountIdentityService(repository, cipher);
    }

    @Test
    @DisplayName("HMAC 조회가 성공하면 평문 fallback과 재암호화를 수행하지 않는다")
    void findKakaoAccount_currentIdentity_returnsDirectly() {
        SocialAccount account = mock(SocialAccount.class);
        when(account.matchesProviderUserKey(IDENTIFIER.hash(), IDENTIFIER.keyVersion()))
                .thenReturn(true);
        when(repository.findByProviderAndProviderUserKey(SocialProvider.KAKAO, IDENTIFIER.hash()))
                .thenReturn(Optional.of(account));

        assertThat(service.findKakaoAccount(PROVIDER_USER_ID, IDENTIFIER)).containsSame(account);

        verify(repository, never()).findByProviderAndLegacyProviderUserId(any(), any());
        verify(cipher, never()).encrypt(any());
    }

    @Test
    @DisplayName("기존 평문 row는 hashKakao 결과와 암호문으로 lazy backfill한다")
    void findKakaoAccount_legacyIdentity_backfillsLifecycle() {
        SocialAccount legacy = mock(SocialAccount.class);
        when(repository.findByProviderAndProviderUserKey(SocialProvider.KAKAO, IDENTIFIER.hash()))
                .thenReturn(Optional.empty());
        when(repository.findByProviderAndLegacyProviderUserId(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.of(legacy));
        when(legacy.requiresLegacyIdentityBackfill()).thenReturn(true);
        when(cipher.encrypt(PROVIDER_USER_ID)).thenReturn(ENCRYPTED);

        assertThat(service.findKakaoAccount(PROVIDER_USER_ID, IDENTIFIER)).containsSame(legacy);

        verify(legacy)
                .backfillLegacyIdentity(
                        eq(IDENTIFIER.hash()),
                        eq(IDENTIFIER.keyVersion()),
                        eq(ENCRYPTED),
                        any(LocalDateTime.class));
    }
}
