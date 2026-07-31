package com.gather.gather.domain.auth.kakao.admin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KakaoAdminPropertiesTest {

    private static final String TEST_KEY = "unit-test-admin-key";

    @Test
    void constructor_whenDisabledWithoutKey_succeeds() {
        KakaoAdminProperties properties = properties(false, null, "https://kapi.kakao.com");

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.key()).isNull();
    }

    @Test
    void constructor_whenEnabledWithoutKey_failsWithoutSecretExposure() {
        assertThatThrownBy(() -> properties(true, " ", "https://kapi.kakao.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_ADMIN_KEY")
                .hasMessageNotContaining(TEST_KEY);
    }

    @Test
    void constructor_withHttpsBaseUrl_succeeds() {
        KakaoAdminProperties properties = properties(false, null, "https://kapi.kakao.com");

        assertThat(properties.apiBaseUrl()).hasToString("https://kapi.kakao.com");
    }

    @Test
    void constructor_withTrailingSlashBaseUrl_succeeds() {
        KakaoAdminProperties properties = properties(false, null, "https://kapi.kakao.com/");

        assertThat(properties.apiBaseUrl()).hasToString("https://kapi.kakao.com/");
        assertThat(properties.toString()).contains("https://kapi.kakao.com");
    }

    @Test
    void toString_masksAdminKeyAndDoesNotContainUriSensitiveComponents() {
        KakaoAdminProperties properties = properties(true, TEST_KEY, "https://kapi.kakao.com");

        assertThat(properties.toString()).contains("key=****").doesNotContain(TEST_KEY);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBaseUrls")
    void constructor_withInvalidBaseUrl_failsWithoutExposingUrl(String name, String baseUrl) {
        assertThatThrownBy(() -> properties(false, null, baseUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid Kakao Admin API base URL")
                .hasMessageNotContaining(baseUrl)
                .hasMessageNotContaining("secret");
    }

    static Stream<Arguments> invalidBaseUrls() {
        return Stream.of(
                Arguments.of("http scheme", "http://kapi.kakao.com"),
                Arguments.of("ftp scheme", "ftp://kapi.kakao.com"),
                Arguments.of("relative URL", "/relative/kapi"),
                Arguments.of("missing host", "https:///kapi"),
                Arguments.of("user info", "https://user:secret@kapi.kakao.com"),
                Arguments.of("query", "https://kapi.kakao.com?token=secret"),
                Arguments.of("fragment", "https://kapi.kakao.com#secret"),
                Arguments.of("path", "https://kapi.kakao.com/admin"));
    }

    @ParameterizedTest
    @MethodSource("nonPositiveTimeouts")
    void constructor_withNonPositiveConnectTimeout_fails(Duration timeout) {
        assertThatThrownBy(
                        () ->
                                new KakaoAdminProperties(
                                        false,
                                        null,
                                        URI.create("https://kapi.kakao.com"),
                                        timeout,
                                        Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connect-timeout");
    }

    @ParameterizedTest
    @MethodSource("nonPositiveTimeouts")
    void constructor_withNonPositiveReadTimeout_fails(Duration timeout) {
        assertThatThrownBy(
                        () ->
                                new KakaoAdminProperties(
                                        false,
                                        null,
                                        URI.create("https://kapi.kakao.com"),
                                        Duration.ofSeconds(2),
                                        timeout))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-timeout");
    }

    static Stream<Arguments> nonPositiveTimeouts() {
        return Stream.of(Arguments.of(Duration.ZERO), Arguments.of(Duration.ofSeconds(-1)));
    }

    private KakaoAdminProperties properties(boolean enabled, String key, String baseUrl) {
        return new KakaoAdminProperties(
                enabled, key, URI.create(baseUrl), Duration.ofSeconds(2), Duration.ofSeconds(5));
    }
}
