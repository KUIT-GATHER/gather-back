package com.gather.gather.domain.auth.config;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "gather.auth.rejoin-block-hmac")
public record RejoinBlockHmacProperties(String secret, @DefaultValue("1") int keyVersion) {

    private static final int MIN_SECRET_BYTE_LENGTH = 32;

    public RejoinBlockHmacProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("gather.auth.rejoin-block-hmac.secret이 설정되지 않았습니다.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "gather.auth.rejoin-block-hmac.secret은 Base64 형식이어야 합니다.", exception);
        }
        if (decoded.length < MIN_SECRET_BYTE_LENGTH) {
            throw new IllegalStateException(
                    "gather.auth.rejoin-block-hmac.secret은 Base64 디코딩 후 최소 "
                            + MIN_SECRET_BYTE_LENGTH
                            + "바이트 이상이어야 합니다.");
        }
        if (keyVersion <= 0) {
            throw new IllegalStateException(
                    "gather.auth.rejoin-block-hmac.key-version은 1 이상이어야 합니다.");
        }
    }

    @Override
    public String toString() {
        return "RejoinBlockHmacProperties[secret=****, keyVersion=" + keyVersion + "]";
    }
}
