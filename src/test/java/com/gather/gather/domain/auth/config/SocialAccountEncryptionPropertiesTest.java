package com.gather.gather.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialAccountEncryptionPropertiesTest {

    @Test
    @DisplayName("AES-256용 32바이트 Base64 키와 양수 버전을 허용하고 toString에서 키를 가린다")
    void validProperties_maskKey() {
        String key = base64Key(32);

        SocialAccountEncryptionProperties properties =
                new SocialAccountEncryptionProperties(key, 3);

        assertThat(properties.toString()).doesNotContain(key).contains("key=****", "keyVersion=3");
    }

    @Test
    @DisplayName("Base64가 아니거나 32바이트가 아닌 키는 거부한다")
    void invalidKey_isRejected() {
        assertThatThrownBy(() -> new SocialAccountEncryptionProperties("not-base64", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not-base64");
        assertThatThrownBy(() -> new SocialAccountEncryptionProperties(base64Key(31), 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SocialAccountEncryptionProperties(base64Key(33), 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("0 이하 암호화 키 버전은 거부한다")
    void nonPositiveKeyVersion_isRejected() {
        assertThatThrownBy(() -> new SocialAccountEncryptionProperties(base64Key(32), 0))
                .isInstanceOf(IllegalStateException.class);
    }

    private String base64Key(int length) {
        return Base64.getEncoder().encodeToString(new byte[length]);
    }
}
