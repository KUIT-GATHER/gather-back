package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.kakao.dto.SignupStatus;
import com.gather.gather.domain.auth.service.TokenIssueResult;

/** 카카오 로그인 분기 결과. 기존 회원이면 tokens가, 신규 회원이면 signupToken이 채워진다. */
public record KakaoLoginResult(
        SignupStatus signupStatus, TokenIssueResult tokens, String signupToken, String nickname) {

    public static KakaoLoginResult loginCompleted(TokenIssueResult tokens) {
        return new KakaoLoginResult(SignupStatus.LOGIN_COMPLETED, tokens, null, null);
    }

    public static KakaoLoginResult additionalInfoRequired(String signupToken, String nickname) {
        return new KakaoLoginResult(
                SignupStatus.ADDITIONAL_INFO_REQUIRED, null, signupToken, nickname);
    }

    public boolean isLoginCompleted() {
        return signupStatus == SignupStatus.LOGIN_COMPLETED;
    }
}
