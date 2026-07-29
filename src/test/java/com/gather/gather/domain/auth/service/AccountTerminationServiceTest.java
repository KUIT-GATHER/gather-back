package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountTerminationServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private EmailVerificationRepository emailVerificationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AccountTerminationService accountTerminationService;

    private User activeUser() {
        User user =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "test@example.com",
                        "encoded-password",
                        "길동",
                        "소개글",
                        true,
                        true,
                        false,
                        Region.create("강남구", 2, "11680", null),
                        List.of(PostingCategory.WELFARE));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    @Test
    @DisplayName("탈퇴 처리하면 상태 전이·토큰 삭제·이벤트 발행이 모두 수행된다")
    void terminate_transitionsAndPublishes() {
        User user = activeUser();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        LocalDateTime before = LocalDateTime.now();
        accountTerminationService.terminate(USER_ID, WithdrawalReason.SELF);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.getWithdrawnAt()).isBetween(before, LocalDateTime.now());
        verify(refreshTokenRepository).deleteByUser(user);
        verify(emailVerificationRepository).deleteByEmail("test@example.com");

        ArgumentCaptor<UserWithdrawnEvent> captor =
                ArgumentCaptor.forClass(UserWithdrawnEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("웹훅 경로는 탈퇴 사유가 KAKAO_UNLINK로 기록된다")
    void terminate_recordsGivenReason() {
        User user = activeUser();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        accountTerminationService.terminate(USER_ID, WithdrawalReason.KAKAO_UNLINK);

        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.KAKAO_UNLINK);
    }

    @Test
    @DisplayName("이미 탈퇴한 계정이면 사유를 덮어쓰지 않고 이벤트도 다시 발행하지 않는다")
    void terminate_alreadyWithdrawn_isNoOp() {
        User user = activeUser();
        LocalDateTime firstWithdrawnAt = LocalDateTime.of(2026, 7, 20, 12, 0);
        user.withdraw(WithdrawalReason.SELF, firstWithdrawnAt);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        accountTerminationService.terminate(USER_ID, WithdrawalReason.KAKAO_UNLINK);

        assertThat(user.getWithdrawalReason()).isEqualTo(WithdrawalReason.SELF);
        assertThat(user.getWithdrawnAt()).isEqualTo(firstWithdrawnAt);
        verify(refreshTokenRepository, never()).deleteByUser(any());
        verify(emailVerificationRepository, never()).deleteByEmail(any());
        verify(eventPublisher, never()).publishEvent(any(UserWithdrawnEvent.class));
    }

    @Test
    @DisplayName("사용자가 없으면 USER_NOT_FOUND를 던진다")
    void terminate_userNotFound_throws() {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> accountTerminationService.terminate(USER_ID, WithdrawalReason.SELF))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
