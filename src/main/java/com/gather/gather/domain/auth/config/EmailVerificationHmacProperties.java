package com.gather.gather.domain.auth.config;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 이메일 인증 코드 HMAC 전용 설정. 다른 용도의 키와 섞이면 한 키 유출이 전체로 번지므로 재가입 제한·JWT 키와 분리한다. */
@ConfigurationProperties(prefix = "gather.auth.email-verification-hmac")
public record EmailVerificationHmacProperties(String secret) {

    private static final int MIN_SECRET_BYTE_LENGTH = 32;

    public EmailVerificationHmacProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "gather.auth.email-verification-hmac.secret이 설정되지 않았습니다.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "gather.auth.email-verification-hmac.secret은 Base64 형식이어야 합니다.", exception);
        }
        if (decoded.length < MIN_SECRET_BYTE_LENGTH) {
            throw new IllegalStateException(
                    "gather.auth.email-verification-hmac.secret은 Base64 디코딩 후 최소 "
                            + MIN_SECRET_BYTE_LENGTH
                            + "바이트 이상이어야 합니다.");
        }
    }

    @Override
    public String toString() {
        return "EmailVerificationHmacProperties[secret=****]";
    }
}
