package com.gather.gather.domain.auth.service;

record AccountRecoveryTransactionResult(Outcome outcome, String email) {

    enum Outcome {
        EMAIL,
        KAKAO,
        ACCOUNT_NOT_FOUND
    }

    static AccountRecoveryTransactionResult email(String email) {
        return new AccountRecoveryTransactionResult(Outcome.EMAIL, email);
    }

    static AccountRecoveryTransactionResult kakao() {
        return new AccountRecoveryTransactionResult(Outcome.KAKAO, null);
    }

    static AccountRecoveryTransactionResult accountNotFound() {
        return new AccountRecoveryTransactionResult(Outcome.ACCOUNT_NOT_FOUND, null);
    }
}
