package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("이메일과 비밀번호가 있으면 소셜 계정보다 EMAIL을 우선한다")
    void resolve_prefersEmailCredentials() {
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPassword()).thenReturn("encoded-password");

        assertThat(resolver.resolve(user)).contains(AccountLoginType.EMAIL);
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("이메일 credential 없이 연결된 카카오 계정이 있으면 KAKAO다")
    void resolve_returnsKakaoForLinkedKakaoOnlyAccount() {
        when(user.getId()).thenReturn(1L);
        when(socialAccountRepository.existsByUserIdAndProviderAndLinkStatus(
                        1L, SocialProvider.KAKAO, SocialAccountLinkStatus.LINKED))
                .thenReturn(true);

        assertThat(resolver.resolve(user)).contains(AccountLoginType.KAKAO);
    }

    @Test
    @DisplayName("이메일과 비밀번호 중 하나만 있으면 정상 계정으로 분류하지 않는다")
    void resolve_rejectsPartialEmailCredentials() {
        when(user.getEmail()).thenReturn("user@example.com");

        assertThat(resolver.resolve(user)).isEmpty();
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("credential과 연결된 카카오 계정이 모두 없으면 분류하지 않는다")
    void resolve_rejectsAccountWithoutLoginMethod() {
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
}
