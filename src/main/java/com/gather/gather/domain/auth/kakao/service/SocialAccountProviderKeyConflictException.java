package com.gather.gather.domain.auth.kakao.service;

import org.springframework.dao.DataIntegrityViolationException;

final class SocialAccountProviderKeyConflictException extends RuntimeException {

    SocialAccountProviderKeyConflictException(DataIntegrityViolationException integrityException) {
        super("소셜 계정 조회 키가 동시에 등록되었습니다.", integrityException);
    }

    DataIntegrityViolationException integrityException() {
        return (DataIntegrityViolationException) getCause();
    }
}
