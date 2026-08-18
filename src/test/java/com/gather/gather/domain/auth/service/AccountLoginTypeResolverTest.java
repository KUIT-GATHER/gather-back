package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountLoginTypeResolverTest {

    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private User user;

    private AccountLoginTypeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AccountLoginTypeResolver(socialAccountRepository);
    }

    @Test
    @DisplayName("이메일과 비밀번호가 있으면 소셜 계정보다 EMAIL을 우선한다")
    void resolve_prefersEmailCredentials() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPassword()).thenReturn("encoded-password");

        assertThat(resolver.resolve(user)).contains(AccountLoginType.EMAIL);
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("이메일 credential 없이 연결된 카카오 계정이 있으면 KAKAO다")
    void resolve_returnsKakaoForLinkedKakaoOnlyAccount() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getId()).thenReturn(1L);
        when(socialAccountRepository.existsByUserIdAndProviderAndLinkStatus(
                        1L, SocialProvider.KAKAO, SocialAccountLinkStatus.LINKED))
                .thenReturn(true);

        assertThat(resolver.resolve(user)).contains(AccountLoginType.KAKAO);
    }

    @Test
    @DisplayName("이메일과 비밀번호 중 하나만 있으면 정상 계정으로 분류하지 않는다")
    void resolve_rejectsPartialEmailCredentials() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getEmail()).thenReturn("user@example.com");

        assertThat(resolver.resolve(user)).isEmpty();
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("credential과 연결된 카카오 계정이 모두 없으면 분류하지 않는다")
    void resolve_rejectsAccountWithoutLoginMethod() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getId()).thenReturn(1L);

        assertThat(resolver.resolve(user)).isEmpty();
    }

    @Test
    @DisplayName("ACTIVE가 아닌 계정은 credential을 조회하지 않고 분류하지 않는다")
    void resolve_rejectsInactiveAccount() {
        when(user.getStatus()).thenReturn(UserStatus.SUSPENDED);

        assertThat(resolver.resolve(user)).isEmpty();
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("credential 판정은 이메일과 비밀번호가 있으면 EMAIL이다")
    void resolveCredentialType_returnsEmailForEmailCredentials() {
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPassword()).thenReturn("encoded-password");

        assertThat(resolver.resolveCredentialType(user)).contains(AccountLoginType.EMAIL);
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("credential 판정은 연결된 카카오만 있으면 KAKAO다")
    void resolveCredentialType_returnsKakaoForLinkedKakaoOnlyAccount() {
        when(user.getId()).thenReturn(1L);
        when(socialAccountRepository.existsByUserIdAndProviderAndLinkStatus(
                        1L, SocialProvider.KAKAO, SocialAccountLinkStatus.LINKED))
                .thenReturn(true);

        assertThat(resolver.resolveCredentialType(user)).contains(AccountLoginType.KAKAO);
    }

    @Test
    @DisplayName("SUSPENDED 계정도 이메일 credential이 정상이면 EMAIL로 판정한다")
    void resolveCredentialType_ignoresSuspendedStatus() {
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPassword()).thenReturn("encoded-password");

        assertThat(resolver.resolveCredentialType(user)).contains(AccountLoginType.EMAIL);
        // 상태를 보지 않아야 마이페이지에서 SUSPENDED 계정의 loginType이 사라지지 않는다.
        verify(user, never()).getStatus();
    }

    @Test
    @DisplayName("SUSPENDED 카카오 전용 계정도 KAKAO로 판정한다")
    void resolveCredentialType_returnsKakaoForSuspendedKakaoOnlyAccount() {
        when(user.getId()).thenReturn(1L);
        when(socialAccountRepository.existsByUserIdAndProviderAndLinkStatus(
                        1L, SocialProvider.KAKAO, SocialAccountLinkStatus.LINKED))
                .thenReturn(true);

        assertThat(resolver.resolveCredentialType(user)).contains(AccountLoginType.KAKAO);
        verify(user, never()).getStatus();
    }

    @Test
    @DisplayName("credential 판정도 부분 credential은 분류하지 않는다")
    void resolveCredentialType_rejectsPartialEmailCredentials() {
        when(user.getPassword()).thenReturn("encoded-password");

        assertThat(resolver.resolveCredentialType(user)).isEmpty();
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("credential 판정도 로그인 수단이 전혀 없으면 분류하지 않는다")
    void resolveCredentialType_rejectsAccountWithoutLoginMethod() {
        when(user.getId()).thenReturn(1L);

        assertThat(resolver.resolveCredentialType(user)).isEmpty();
    }
}
