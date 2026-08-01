package com.gather.gather.domain.auth.entity;

public record EncryptedProviderUserId(String ciphertext, int keyVersion) {

    public EncryptedProviderUserId {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("암호화된 소셜 식별자는 필수입니다.");
        }
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("암호화 키 버전은 1 이상이어야 합니다.");
        }
    }
}
