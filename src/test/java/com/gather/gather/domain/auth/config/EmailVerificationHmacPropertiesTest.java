package com.gather.gather.domain.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailVerificationHmacPropertiesTest {

    private static final String VALID_SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String SENSITIVE_SECRET =
            Base64.getEncoder().encodeToString("email-verification-secret-marker".getBytes());

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("시크릿이 없으면 기동에 실패한다")
    void construct_missingSecret_throws(String secret) {
        assertThatThrownBy(() -> new EmailVerificationHmacProperties(secret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gather.auth.email-verification-hmac.secret");
    }

    @Test
    @DisplayName("Base64가 아니면 기동에 실패한다")
    void construct_invalidBase64_throws() {
        assertThatThrownBy(() -> new EmailVerificationHmacProperties("not-base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    @DisplayName("디코딩 후 32바이트 미만이면 기동에 실패한다")
    void construct_secretShorterThan32Bytes_throws() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> new EmailVerificationHmacProperties(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("디코딩 후 32바이트 이상이면 생성된다")
    void construct_secretOf32Bytes_succeeds() {
        assertThatCode(() -> new EmailVerificationHmacProperties(VALID_SECRET))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("toString에 시크릿이 노출되지 않는다")
    void toString_masksSecret() {
        EmailVerificationHmacProperties properties =
                new EmailVerificationHmacProperties(SENSITIVE_SECRET);

        assertThat(properties.toString()).doesNotContain(SENSITIVE_SECRET).contains("****");
    }

    @Test
    @DisplayName("검증 실패 메시지에 시크릿이 노출되지 않는다")
    void construct_failureMessage_doesNotExposeSecret() {
        String shortSensitiveSecret = Base64.getEncoder().encodeToString("short-marker".getBytes());

        assertThatThrownBy(() -> new EmailVerificationHmacProperties(shortSensitiveSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(shortSensitiveSecret);
    }
}
