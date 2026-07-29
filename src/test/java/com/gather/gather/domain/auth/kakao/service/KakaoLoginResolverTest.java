package com.gather.gather.domain.auth.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenProvider;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.LoginPolicy;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.auth.service.TokenIssuer;
import com.gather.gather.domain.auth.service.WithdrawalPolicy;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KakaoLoginResolverTest {

    private static final String PROVIDER_USER_ID = "123456789";

    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private UserRepository userRepository;
    @Mock private SocialSignupTokenProvider socialSignupTokenProvider;
    @Mock private TokenIssuer tokenIssuer;

    private KakaoLoginResolver resolver() {
        return new KakaoLoginResolver(
                socialAccountRepository,
                userRepository,
                socialSignupTokenProvider,
                tokenIssuer,
                new LoginPolicy(),
                new WithdrawalPolicy());
    }

    @Test
    void resolve_withinSevenDays_blocksWithdrawnUser() {
        User user = withdrawnUser(LocalDateTime.now().minusDays(7).plusMinutes(1));
        stubLockedAccount(user);

        assertThatThrownBy(
                        () ->
                                resolver()
                                        .resolve(
                                                SocialProvider.KAKAO, PROVIDER_USER_ID, "nickname"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.WITHDRAWN_USER));
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void resolve_atSevenDays_deletesStaleAccountAndReturnsSignupToken() {
        User user = withdrawnUser(LocalDateTime.now().minusDays(7));
        SocialAccount account = stubLockedAccount(user);
        when(socialSignupTokenProvider.createSignupToken(SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn("signup-token");

        KakaoLoginResult result =
                resolver().resolve(SocialProvider.KAKAO, PROVIDER_USER_ID, "nickname");

        assertThat(result.signupToken()).isEqualTo("signup-token");
        verify(socialAccountRepository).delete(account);
    }

    @Test
    void resolve_afterSevenDays_deletesStaleAccountAndReturnsSignupToken() {
        User user = withdrawnUser(LocalDateTime.now().minusDays(8));
        SocialAccount account = stubLockedAccount(user);
        when(socialSignupTokenProvider.createSignupToken(SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn("signup-token");

        KakaoLoginResult result =
                resolver().resolve(SocialProvider.KAKAO, PROVIDER_USER_ID, "nickname");

        assertThat(result.signupToken()).isEqualTo("signup-token");
        verify(socialAccountRepository).delete(account);
    }

    @Test
    void resolve_activeUser_issuesExistingLoginTokens() {
        User user = activeUser();
        stubLockedAccount(user);
        when(tokenIssuer.issue(user)).thenReturn(new TokenIssueResult("access", "refresh"));

        KakaoLoginResult result =
                resolver().resolve(SocialProvider.KAKAO, PROVIDER_USER_ID, "nickname");

        assertThat(result.isLoginCompleted()).isTrue();
        verify(tokenIssuer).issue(user);
    }

    @Test
    void resolve_suspendedUser_preservesExistingBlockPolicy() {
        User user = activeUser();
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        stubLockedAccount(user);

        assertThatThrownBy(
                        () ->
                                resolver()
                                        .resolve(
                                                SocialProvider.KAKAO, PROVIDER_USER_ID, "nickname"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SUSPENDED_USER));
    }

    private SocialAccount stubLockedAccount(User user) {
        SocialAccount account = SocialAccount.create(user, SocialProvider.KAKAO, PROVIDER_USER_ID);
        when(socialAccountRepository.findByProviderAndProviderUserIdForUpdate(
                        SocialProvider.KAKAO, PROVIDER_USER_ID))
                .thenReturn(Optional.of(account));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        return account;
    }

    private User withdrawnUser(LocalDateTime withdrawnAt) {
        User user = activeUser();
        user.withdraw(WithdrawalReason.SELF, withdrawnAt);
        return user;
    }

    private User activeUser() {
        User user =
                User.create(
                        "Test",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "test@example.com",
                        "encoded-password",
                        "nickname",
                        null,
                        true,
                        true,
                        false,
                        Region.create("Gangnam", 2, "11680", null),
                        List.of(PostingCategory.WELFARE));
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
