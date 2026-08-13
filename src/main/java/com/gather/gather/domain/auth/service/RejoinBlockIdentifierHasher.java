package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.config.RejoinBlockHmacProperties;
import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class RejoinBlockIdentifierHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KAKAO_NAMESPACE = "KAKAO:";

    private final SecretKeySpec secretKey;
    private final int keyVersion;
    private final PhoneNumberPolicy phoneNumberPolicy;

    public RejoinBlockIdentifierHasher(
            RejoinBlockHmacProperties properties, PhoneNumberPolicy phoneNumberPolicy) {
        this.secretKey =
                new SecretKeySpec(Base64.getDecoder().decode(properties.secret()), HMAC_ALGORITHM);
        this.keyVersion = properties.keyVersion();
        this.phoneNumberPolicy = phoneNumberPolicy;
    }

    public RejoinBlockIdentifier hashPhone(String phoneNumber) {
        String normalizedPhoneNumber = phoneNumberPolicy.normalize(phoneNumber);
        return hash(AccountRejoinBlockIdentifierType.PHONE, normalizedPhoneNumber);
    }

    public RejoinBlockIdentifier hashKakao(String providerUserId) {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("카카오 식별자는 필수입니다.");
        }
        return hash(AccountRejoinBlockIdentifierType.KAKAO, KAKAO_NAMESPACE + providerUserId);
    }

    private RejoinBlockIdentifier hash(
            AccountRejoinBlockIdentifierType type, String namespacedIdentifier) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] digest = mac.doFinal(namespacedIdentifier.getBytes(StandardCharsets.UTF_8));
            return new RejoinBlockIdentifier(type, HexFormat.of().formatHex(digest), keyVersion);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256을 사용할 수 없습니다.", exception);
        }
    }
}
