package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LockedTokenIssuanceServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private TokenIssuer tokenIssuer;

    @Test
    @DisplayName("탈퇴 처리 중인 User는 잠금 아래 재검증 후 카카오 토큰 발급을 차단한다")
    void issueForSocialAccount_whenWithdrawalPending_rejectsBeforeSocialAccountLock() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getStatus()).thenReturn(UserStatus.WITHDRAWAL_PENDING);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        LockedTokenIssuanceService service = service();

        assertThatThrownBy(() -> service.issueForSocialAccount(1L, 10L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.WITHDRAWAL_PENDING_USER));

        verify(socialAccountRepository, never()).findByIdForUpdate(10L);
        verify(tokenIssuer, never()).issue(user);
    }

    @Test
    @DisplayName("User 다음 SocialAccount 순서로 잠그고 최신 연결 상태를 검증한다")
    void issueForSocialAccount_whenUnlinkPending_rejectsWithoutToken() {
        User user = org.mockito.Mockito.mock(User.class);
        SocialAccount socialAccount = org.mockito.Mockito.mock(SocialAccount.class);
        when(user.getId()).thenReturn(1L);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(socialAccount.getUser()).thenReturn(user);
        when(socialAccount.isLinked()).thenReturn(false);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(socialAccountRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(socialAccount));
        LockedTokenIssuanceService service = service();

        assertThatThrownBy(() -> service.issueForSocialAccount(1L, 10L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED));

        verify(tokenIssuer, never()).issue(user);
    }

    private LockedTokenIssuanceService service() {
        return new LockedTokenIssuanceService(
                userRepository, socialAccountRepository, tokenIssuer, new LoginPolicy());
    }
}
