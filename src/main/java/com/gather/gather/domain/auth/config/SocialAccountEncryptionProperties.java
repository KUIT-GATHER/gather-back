package com.gather.gather.domain.auth.config;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "gather.auth.social-account-encryption")
public record SocialAccountEncryptionProperties(String key, @DefaultValue("1") int keyVersion) {

    private static final int AES_256_KEY_BYTE_LENGTH = 32;

    public SocialAccountEncryptionProperties {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "gather.auth.social-account-encryption.key가 설정되지 않았습니다.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "gather.auth.social-account-encryption.key는 Base64 형식이어야 합니다.", exception);
        }
        if (decoded.length != AES_256_KEY_BYTE_LENGTH) {
            throw new IllegalStateException(
                    "gather.auth.social-account-encryption.key는 Base64 디코딩 후 정확히 "
                            + AES_256_KEY_BYTE_LENGTH
                            + "바이트여야 합니다.");
        }
        if (keyVersion <= 0) {
            throw new IllegalStateException(
                    "gather.auth.social-account-encryption.key-version은 1 이상이어야 합니다.");
        }
    }

    @Override
    public String toString() {
        return "SocialAccountEncryptionProperties[key=****, keyVersion=" + keyVersion + "]";
    }
}
