package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    void resolveForActiveAccount_prefersEmailCredentials() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPassword()).thenReturn("encoded-password");

        assertThat(resolver.resolveForActiveAccount(user)).contains(AccountLoginType.EMAIL);
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("이메일 credential 없이 연결된 카카오 계정이 있으면 KAKAO다")
    void resolveForActiveAccount_returnsKakaoForLinkedKakaoOnlyAccount() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getId()).thenReturn(1L);
        when(socialAccountRepository.existsByUserIdAndProviderAndLinkStatus(
                        1L, SocialProvider.KAKAO, SocialAccountLinkStatus.LINKED))
                .thenReturn(true);

        assertThat(resolver.resolveForActiveAccount(user)).contains(AccountLoginType.KAKAO);
    }

    @Test
    @DisplayName("이메일과 비밀번호 중 하나만 있으면 정상 계정으로 분류하지 않는다")
    void resolveForActiveAccount_rejectsPartialEmailCredentials() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getEmail()).thenReturn("user@example.com");

        assertThat(resolver.resolveForActiveAccount(user)).isEmpty();
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("credential과 연결된 카카오 계정이 모두 없으면 분류하지 않는다")
    void resolveForActiveAccount_rejectsAccountWithoutLoginMethod() {
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getId()).thenReturn(1L);

        assertThat(resolver.resolveForActiveAccount(user)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = UserStatus.class,
            names = {"SUSPENDED", "WITHDRAWAL_PENDING", "WITHDRAWN"})
    @DisplayName("ACTIVE가 아닌 계정은 credential을 조회하지 않고 분류하지 않는다")
    void resolveForActiveAccount_rejectsInactiveAccount(UserStatus status) {
        when(user.getStatus()).thenReturn(status);

        assertThat(resolver.resolveForActiveAccount(user)).isEmpty();
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("credential 판정은 이메일과 비밀번호가 있으면 EMAIL이다")
    void resolveCredentialTypeIgnoringStatus_returnsEmailForEmailCredentials() {
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPassword()).thenReturn("encoded-password");

        assertThat(resolver.resolveCredentialTypeIgnoringStatus(user))
                .contains(AccountLoginType.EMAIL);
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("credential 판정은 연결된 카카오만 있으면 KAKAO다")
    void resolveCredentialTypeIgnoringStatus_returnsKakaoForLinkedKakaoOnlyAccount() {
        when(user.getId()).thenReturn(1L);
        when(socialAccountRepository.existsByUserIdAndProviderAndLinkStatus(
                        1L, SocialProvider.KAKAO, SocialAccountLinkStatus.LINKED))
                .thenReturn(true);

        assertThat(resolver.resolveCredentialTypeIgnoringStatus(user))
                .contains(AccountLoginType.KAKAO);
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("credential 판정은 계정 상태와 무관하게 이메일 credential을 EMAIL로 판정한다")
    void resolveCredentialTypeIgnoringStatus_ignoresUserStatus(UserStatus status) {
        // 상태를 보지 않아야 마이페이지에서 SUSPENDED·탈퇴 진행 계정의 loginType이 사라지지 않는다.
        User emailUser = emailUserWithStatus(status);

        assertThat(resolver.resolveCredentialTypeIgnoringStatus(emailUser))
                .contains(AccountLoginType.EMAIL);
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("SUSPENDED 카카오 전용 계정도 KAKAO로 판정한다")
    void resolveCredentialTypeIgnoringStatus_returnsKakaoForSuspendedKakaoOnlyAccount() {
        when(user.getId()).thenReturn(1L);
        when(socialAccountRepository.existsByUserIdAndProviderAndLinkStatus(
                        1L, SocialProvider.KAKAO, SocialAccountLinkStatus.LINKED))
                .thenReturn(true);

        assertThat(resolver.resolveCredentialTypeIgnoringStatus(user))
                .contains(AccountLoginType.KAKAO);
        verify(user, never()).getStatus();
    }

    @Test
    @DisplayName("credential 판정도 부분 credential은 분류하지 않는다")
    void resolveCredentialTypeIgnoringStatus_rejectsPartialEmailCredentials() {
        when(user.getPassword()).thenReturn("encoded-password");

        assertThat(resolver.resolveCredentialTypeIgnoringStatus(user)).isEmpty();
        verifyNoInteractions(socialAccountRepository);
    }

    @Test
    @DisplayName("credential 판정도 로그인 수단이 전혀 없으면 분류하지 않는다")
    void resolveCredentialTypeIgnoringStatus_rejectsAccountWithoutLoginMethod() {
        when(user.getId()).thenReturn(1L);

        assertThat(resolver.resolveCredentialTypeIgnoringStatus(user)).isEmpty();
    }

    private User emailUserWithStatus(UserStatus status) {
        User emailUser =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "user@example.com",
                        "encoded-password",
                        "길동",
                        null,
                        true,
                        true,
                        false,
                        null,
                        List.of());
        ReflectionTestUtils.setField(emailUser, "status", status);
        return emailUser;
    }
}
