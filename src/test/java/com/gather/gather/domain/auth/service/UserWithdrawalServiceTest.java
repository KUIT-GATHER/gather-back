package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.service.KakaoUnlinkService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private AccountTerminationService accountTerminationService;
    @Mock private KakaoUnlinkService kakaoUnlinkService;

    @InjectMocks private UserWithdrawalService userWithdrawalService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("계정 종료를 먼저 커밋한 뒤에 카카오 연결을 해제한다")
    void withdrawMyAccount_terminatesBeforeUnlink() {
        userWithdrawalService.withdrawMyAccount();

        InOrder inOrder = inOrder(accountTerminationService, kakaoUnlinkService);
        inOrder.verify(accountTerminationService).terminate(USER_ID, WithdrawalReason.SELF);
        inOrder.verify(kakaoUnlinkService).unlinkIfLinked(USER_ID);
    }

    @Test
    @DisplayName("카카오 연결 해제가 실패해도 이미 커밋된 탈퇴를 실패로 만들지 않는다")
    void withdrawMyAccount_whenUnlinkFails_doesNotPropagate() {
        doThrow(new IllegalStateException("kakao down"))
                .when(kakaoUnlinkService)
                .unlinkIfLinked(USER_ID);

        assertThatCode(() -> userWithdrawalService.withdrawMyAccount()).doesNotThrowAnyException();

        verify(accountTerminationService).terminate(USER_ID, WithdrawalReason.SELF);
    }

    @Test
    @DisplayName("계정 종료가 실패하면 카카오를 호출하지 않는다")
    void withdrawMyAccount_whenTerminationFails_skipsUnlink() {
        doThrow(new IllegalStateException("db down"))
                .when(accountTerminationService)
                .terminate(USER_ID, WithdrawalReason.SELF);

        assertThatCode(() -> userWithdrawalService.withdrawMyAccount())
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(kakaoUnlinkService);
    }
}
