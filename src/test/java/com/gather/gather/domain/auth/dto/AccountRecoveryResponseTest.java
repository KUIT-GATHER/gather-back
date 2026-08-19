package com.gather.gather.domain.auth.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountRecoveryResponseTest {

    @Test
    @DisplayName("EMAIL 응답은 비어 있지 않은 이메일만 허용한다")
    void emailResponse_requiresEmail() {
        assertThatCode(() -> AccountRecoveryResponse.email("user@example.com"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new AccountRecoveryResponse(AccountLoginType.EMAIL, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountRecoveryResponse(AccountLoginType.EMAIL, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("KAKAO 응답에는 이메일을 포함할 수 없다")
    void kakaoResponse_rejectsEmail() {
        assertThatCode(AccountRecoveryResponse::kakao).doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                new AccountRecoveryResponse(
                                        AccountLoginType.KAKAO, "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("로그인 방식이 없는 응답을 허용하지 않는다")
    void response_requiresLoginType() {
        assertThatThrownBy(() -> new AccountRecoveryResponse(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
