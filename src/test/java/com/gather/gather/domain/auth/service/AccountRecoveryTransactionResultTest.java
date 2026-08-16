package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountRecoveryTransactionResultTest {

    @Test
    @DisplayName("EMAIL 결과는 비어 있지 않은 이메일만 허용한다")
    void emailResult_requiresEmail() {
        assertThatCode(() -> AccountRecoveryTransactionResult.email("user@example.com"))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                new AccountRecoveryTransactionResult(
                                        AccountRecoveryTransactionResult.Outcome.EMAIL, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new AccountRecoveryTransactionResult(
                                        AccountRecoveryTransactionResult.Outcome.EMAIL, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EMAIL 외 결과에는 이메일을 포함할 수 없다")
    void nonEmailResult_rejectsEmail() {
        assertThatCode(AccountRecoveryTransactionResult::kakao).doesNotThrowAnyException();
        assertThatCode(AccountRecoveryTransactionResult::accountNotFound)
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                new AccountRecoveryTransactionResult(
                                        AccountRecoveryTransactionResult.Outcome.KAKAO,
                                        "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new AccountRecoveryTransactionResult(
                                        AccountRecoveryTransactionResult.Outcome.ACCOUNT_NOT_FOUND,
                                        "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("결과 유형이 없는 내부 결과를 허용하지 않는다")
    void result_requiresOutcome() {
        assertThatThrownBy(() -> new AccountRecoveryTransactionResult(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
