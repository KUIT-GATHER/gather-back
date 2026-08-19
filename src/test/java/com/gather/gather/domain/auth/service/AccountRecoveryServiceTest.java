package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.dto.AccountRecoveryRequest;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {

    private static final UUID VERIFICATION_ID =
            UUID.fromString("5c5d5db1-4187-43d0-8580-672307994878");

    @Mock private AccountRecoveryTransactionService transactionService;

    private AccountRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new AccountRecoveryService(transactionService);
    }

    @Test
    @DisplayName("트랜잭션의 EMAIL 결과를 API 응답으로 변환한다")
    void recoverEmail_returnsEmailResponse() {
        when(transactionService.recoverEmail(VERIFICATION_ID))
                .thenReturn(AccountRecoveryTransactionResult.email("user@example.com"));

        var response = service.recoverEmail(new AccountRecoveryRequest(VERIFICATION_ID));

        assertThat(response.loginType()).isEqualTo(AccountLoginType.EMAIL);
        assertThat(response.email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("트랜잭션의 KAKAO 결과를 API 응답으로 변환한다")
    void recoverEmail_returnsKakaoResponse() {
        when(transactionService.recoverEmail(VERIFICATION_ID))
                .thenReturn(AccountRecoveryTransactionResult.kakao());

        var response = service.recoverEmail(new AccountRecoveryRequest(VERIFICATION_ID));

        assertThat(response.loginType()).isEqualTo(AccountLoginType.KAKAO);
        assertThat(response.email()).isNull();
    }

    @Test
    @DisplayName("커밋된 ACCOUNT_NOT_FOUND 결과를 공통 비즈니스 예외로 변환한다")
    void recoverEmail_convertsAccountNotFoundAfterTransaction() {
        when(transactionService.recoverEmail(VERIFICATION_ID))
                .thenReturn(AccountRecoveryTransactionResult.accountNotFound());

        assertThatThrownBy(() -> service.recoverEmail(new AccountRecoveryRequest(VERIFICATION_ID)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}
