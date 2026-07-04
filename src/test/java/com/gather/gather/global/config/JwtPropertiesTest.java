package com.gather.gather.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JwtProperties의 기동 시점 검증 계약을 고정한다. 이 생성자가 예외를 던지면 Spring의 바인딩이 실패해 애플리케이션이
 * 기동되지 않으므로, 아래 케이스가 곧 "부팅 실패" 조건이다.
 */
class JwtPropertiesTest {

    // Base64 디코딩 시 64바이트인 유효한 테스트 시크릿.
    private static final String VALID_SECRET =
            "XZWyFEbfHyT37TkUd6Z63CN9wJbT8vlWdmQSzoIZqqGOnAj4ezamA4BO/tChr4bmeE0bbSExFfD8lN/BLitbuQ==";

    @Test
    @DisplayName("secret이 null이면 기동에 실패한다")
    void nullSecret_throws() {
        assertThatThrownBy(() -> new JwtProperties(null, 30))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("secret이 공백이면 기동에 실패한다")
    void blankSecret_throws() {
        assertThatThrownBy(() -> new JwtProperties("   ", 30))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("secret이 Base64가 아니면 기동에 실패한다")
    void nonBase64Secret_throws() {
        assertThatThrownBy(() -> new JwtProperties("not base64 !!!", 30))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("secret이 디코딩 후 32바이트 미만이면 기동에 실패한다")
    void tooShortSecret_throws() {
        // "short" -> 5 bytes
        assertThatThrownBy(() -> new JwtProperties("c2hvcnQ=", 30))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("access-token-validity-minutes가 0 이하이면 기동에 실패한다")
    void nonPositiveValidity_throws() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("유효한 값이면 정상 생성되고 toString은 secret을 마스킹한다")
    void validValues_ok() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, 30);

        assertThat(properties.accessTokenValidityMinutes()).isEqualTo(30);
        assertThat(properties.toString()).doesNotContain(VALID_SECRET).contains("secret=****");
    }
}
