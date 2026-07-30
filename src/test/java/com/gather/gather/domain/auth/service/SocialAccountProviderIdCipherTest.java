package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.config.SocialAccountEncryptionProperties;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import java.util.Base64;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialAccountProviderIdCipherTest {

    private static final String PROVIDER_USER_ID = "123456789";
    private static final String SECRET =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final SocialAccountProviderIdCipher cipher =
            new SocialAccountProviderIdCipher(new SocialAccountEncryptionProperties(SECRET, 4));

    @Test
    @DisplayName("AES-GCM 암호문을 원래 카카오 회원번호로 복호화한다")
    void encryptAndDecrypt_roundTrips() {
        EncryptedProviderUserId encrypted = cipher.encrypt(PROVIDER_USER_ID);

        assertThat(encrypted.keyVersion()).isEqualTo(4);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(PROVIDER_USER_ID);
    }

    @Test
    @DisplayName("랜덤 IV로 동일 원문도 서로 다른 암호문을 만든다")
    void encrypt_samePlaintext_isNonDeterministic() {
        EncryptedProviderUserId first = cipher.encrypt(PROVIDER_USER_ID);
        EncryptedProviderUserId second = cipher.encrypt(PROVIDER_USER_ID);

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(cipher.decrypt(first)).isEqualTo(PROVIDER_USER_ID);
        assertThat(cipher.decrypt(second)).isEqualTo(PROVIDER_USER_ID);
    }

    @Test
    @DisplayName("동일 암복호화 컴포넌트를 여러 스레드에서 안전하게 사용할 수 있다")
    void encryptAndDecrypt_concurrently_isThreadSafe() {
        assertThat(
                        IntStream.range(0, 100)
                                .parallel()
                                .mapToObj(
                                        index ->
                                                cipher.decrypt(
                                                        cipher.encrypt(PROVIDER_USER_ID + index))))
                .containsExactlyInAnyOrderElementsOf(
                        IntStream.range(0, 100)
                                .mapToObj(index -> PROVIDER_USER_ID + index)
                                .toList());
    }

    @Test
    @DisplayName("변조된 암호문은 원문을 노출하지 않는 내부 오류로 실패한다")
    void decrypt_tamperedCiphertext_failsWithoutPlaintext() {
        EncryptedProviderUserId encrypted = cipher.encrypt(PROVIDER_USER_ID);
        byte[] envelope = Base64.getUrlDecoder().decode(encrypted.ciphertext());
        envelope[envelope.length - 1] ^= 1;
        EncryptedProviderUserId tampered =
                new EncryptedProviderUserId(
                        Base64.getUrlEncoder().withoutPadding().encodeToString(envelope),
                        encrypted.keyVersion());

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(SocialAccountCryptoException.class)
                .hasMessageNotContaining(PROVIDER_USER_ID)
                .hasMessageNotContaining(SECRET);
    }

    @Test
    @DisplayName("다른 키 버전과 잘못된 Base64 암호문을 명확히 거부한다")
    void decrypt_invalidEnvelope_fails() {
        assertThatThrownBy(() -> cipher.decrypt(new EncryptedProviderUserId("ciphertext", 5)))
                .isInstanceOf(SocialAccountCryptoException.class);
        assertThatThrownBy(() -> cipher.decrypt(new EncryptedProviderUserId("not+url/base64", 4)))
                .isInstanceOf(SocialAccountCryptoException.class);
    }
}
