package com.gather.gather.domain.auth.service;

import org.springframework.dao.DataIntegrityViolationException;

public class SocialAccountProviderKeyConflictException extends RuntimeException {

    private final DataIntegrityViolationException integrityException;

    public SocialAccountProviderKeyConflictException(
            DataIntegrityViolationException integrityException) {
        super("소셜 계정 조회 키가 동시에 등록되었습니다.", integrityException);
        this.integrityException = integrityException;
    }

    public DataIntegrityViolationException getIntegrityException() {
        return integrityException;
    }
}
