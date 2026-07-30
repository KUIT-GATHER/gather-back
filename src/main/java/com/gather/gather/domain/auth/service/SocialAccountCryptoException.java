package com.gather.gather.domain.auth.service;

public class SocialAccountCryptoException extends RuntimeException {

    public SocialAccountCryptoException(String message) {
        super(message);
    }

    public SocialAccountCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
