package com.gather.gather.domain.auth.service;

record AccountRecoveryTransactionResult(Outcome outcome, String email) {

    AccountRecoveryTransactionResult {
        if (outcome == null) {
            throw new IllegalArgumentException("계정 복구 결과는 필수입니다.");
        }
        boolean hasEmail = email != null && !email.isBlank();
        switch (outcome) {
            case EMAIL -> {
                if (!hasEmail) {
                    throw new IllegalArgumentException("이메일 계정 결과에는 이메일이 필요합니다.");
                }
            }
            case KAKAO, ACCOUNT_NOT_FOUND -> {
                if (email != null) {
                    throw new IllegalArgumentException("이메일 계정 외 결과에는 이메일을 포함할 수 없습니다.");
                }
            }
        }
    }

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
