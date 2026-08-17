package com.gather.gather.domain.auth.service;

record PasswordResetAuthorityResult(Outcome outcome, String rawToken) {

    PasswordResetAuthorityResult {
        if (outcome == null) {
            throw new IllegalArgumentException("비밀번호 재설정 권한 결과는 필수입니다.");
        }
        boolean hasRawToken = rawToken != null && !rawToken.isBlank();
        switch (outcome) {
            case EMAIL -> {
                if (!hasRawToken) {
                    throw new IllegalArgumentException("이메일 계정 결과에는 재설정 토큰이 필요합니다.");
                }
            }
            case KAKAO, ACCOUNT_NOT_FOUND -> {
                if (rawToken != null) {
                    throw new IllegalArgumentException("이메일 계정 외 결과에는 재설정 토큰을 포함할 수 없습니다.");
                }
            }
        }
    }

    enum Outcome {
        EMAIL,
        KAKAO,
        ACCOUNT_NOT_FOUND
    }

    static PasswordResetAuthorityResult email(String rawToken) {
        return new PasswordResetAuthorityResult(Outcome.EMAIL, rawToken);
    }

    static PasswordResetAuthorityResult kakao() {
        return new PasswordResetAuthorityResult(Outcome.KAKAO, null);
    }

    static PasswordResetAuthorityResult accountNotFound() {
        return new PasswordResetAuthorityResult(Outcome.ACCOUNT_NOT_FOUND, null);
    }

    /** 로그나 예외 메시지에 raw token이 실리지 않도록 값을 감춘다. */
    @Override
    public String toString() {
        return "PasswordResetAuthorityResult[outcome="
                + outcome
                + ", rawToken="
                + (rawToken == null ? "null" : "***")
                + "]";
    }
}
