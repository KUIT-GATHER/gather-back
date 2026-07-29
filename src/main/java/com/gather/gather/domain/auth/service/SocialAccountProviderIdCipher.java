package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.config.SocialAccountEncryptionProperties;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SocialAccountProviderIdCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_BYTE_LENGTH = 12;
    private static final int AUTH_TAG_BIT_LENGTH = 128;
    private static final int AUTH_TAG_BYTE_LENGTH = AUTH_TAG_BIT_LENGTH / Byte.SIZE;

    private final SecretKeySpec secretKey;
    private final int keyVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    public SocialAccountProviderIdCipher(SocialAccountEncryptionProperties properties) {
        this.secretKey =
                new SecretKeySpec(Base64.getDecoder().decode(properties.key()), KEY_ALGORITHM);
        this.keyVersion = properties.keyVersion();
    }

    public EncryptedProviderUserId encrypt(String providerUserId) {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("암호화할 소셜 식별자는 필수입니다.");
        }

        byte[] iv = new byte[IV_BYTE_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(AUTH_TAG_BIT_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(providerUserId.getBytes(StandardCharsets.UTF_8));
            byte[] envelope =
                    ByteBuffer.allocate(iv.length + encrypted.length)
                            .put(iv)
                            .put(encrypted)
                            .array();
            return new EncryptedProviderUserId(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(envelope), keyVersion);
        } catch (GeneralSecurityException exception) {
            throw new SocialAccountCryptoException("소셜 식별자를 암호화할 수 없습니다.", exception);
        }
    }

    public String decrypt(EncryptedProviderUserId encryptedProviderUserId) {
        if (encryptedProviderUserId == null) {
            throw new IllegalArgumentException("복호화할 소셜 식별자는 필수입니다.");
        }
        if (encryptedProviderUserId.keyVersion() != keyVersion) {
            throw new SocialAccountCryptoException("지원하지 않는 암호화 키 버전입니다.");
        }

        byte[] envelope;
        try {
            envelope = Base64.getUrlDecoder().decode(encryptedProviderUserId.ciphertext());
        } catch (IllegalArgumentException exception) {
            throw new SocialAccountCryptoException("소셜 식별자 암호문 형식이 올바르지 않습니다.", exception);
        }
        if (envelope.length < IV_BYTE_LENGTH + AUTH_TAG_BYTE_LENGTH) {
            throw new SocialAccountCryptoException("소셜 식별자 암호문 길이가 올바르지 않습니다.");
        }

        byte[] iv = new byte[IV_BYTE_LENGTH];
        byte[] ciphertext = new byte[envelope.length - IV_BYTE_LENGTH];
        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        buffer.get(iv);
        buffer.get(ciphertext);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(AUTH_TAG_BIT_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new SocialAccountCryptoException("소셜 식별자 암호문을 검증할 수 없습니다.", exception);
        }
    }
}
