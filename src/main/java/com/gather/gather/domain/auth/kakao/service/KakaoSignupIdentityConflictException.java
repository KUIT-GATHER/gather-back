package com.gather.gather.domain.auth.kakao.service;

import org.springframework.dao.DataIntegrityViolationException;

final class KakaoSignupIdentityConflictException extends RuntimeException {

    private final SocialSignupIdentitySnapshot identity;

    KakaoSignupIdentityConflictException(
            SocialSignupIdentitySnapshot identity,
            DataIntegrityViolationException integrityException) {
        super("카카오 소셜 계정 식별자가 동시에 등록되었습니다.", integrityException);
        this.identity = identity;
    }

    SocialSignupIdentitySnapshot identity() {
        return identity;
    }

    DataIntegrityViolationException integrityException() {
        return (DataIntegrityViolationException) getCause();
    }
}
