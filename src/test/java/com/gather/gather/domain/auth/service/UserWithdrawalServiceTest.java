package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.event.UserWithdrawnEvent;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private UserRepository userRepository;

    @Mock private RefreshTokenRepository refreshTokenRepository;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("탈퇴 처리 시 상태 전이, refresh token 폐기, 이벤트 발행이 모두 일어난다")
    void withdraw_transitionsStatusRevokesTokensAndPublishesEvent() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            UserWithdrawalService userWithdrawalService =
                    new UserWithdrawalService(
                            userRepository, refreshTokenRepository, eventPublisher);
            User user = mock(User.class);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            userWithdrawalService.withdraw();

            verify(user).withdraw();
            verify(refreshTokenRepository).deleteByUser_Id(USER_ID);
            verify(eventPublisher).publishEvent(new UserWithdrawnEvent(USER_ID));
        }
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외를 던진다")
    void withdraw_throws_whenUserNotFound() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            UserWithdrawalService userWithdrawalService =
                    new UserWithdrawalService(
                            userRepository, refreshTokenRepository, eventPublisher);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(userWithdrawalService::withdraw)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }
}
