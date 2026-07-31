package com.gather.gather.domain.auth.kakao.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KakaoPropertiesTest {

    private static final String REST_API_KEY = "test-rest-api-key";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_URI = "https://gathernow.kr/login/kakao/callback";

    @Test
    @DisplayName("정상 설정은 생성되고 toString에서 비밀값을 마스킹한다")
    void constructor_withValidValues_createsPropertiesAndMasksSecrets() {
        KakaoProperties properties =
                properties(REST_API_KEY, CLIENT_SECRET, List.of(REDIRECT_URI), 900);

        assertThat(properties.redirectUris()).containsExactly(REDIRECT_URI);
        assertThat(properties.signupTokenExpirationSeconds()).isEqualTo(900);
        assertThat(properties.toString())
                .doesNotContain(REST_API_KEY, CLIENT_SECRET)
                .contains("restApiKey=****", "clientSecret=****");
    }

    @Test
    @DisplayName("REST API 키가 없으면 기동 설정 검증에 실패한다")
    void constructor_withoutRestApiKey_throwsIllegalStateException() {
        assertThatThrownBy(() -> properties(" ", CLIENT_SECRET, List.of(REDIRECT_URI), 900))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_REST_API_KEY");
    }

    @Test
    @DisplayName("Client Secret이 없으면 기동 설정 검증에 실패한다")
    void constructor_withoutClientSecret_throwsIllegalStateException() {
        assertThatThrownBy(() -> properties(REST_API_KEY, null, List.of(REDIRECT_URI), 900))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_CLIENT_SECRET");
    }

    @Test
    @DisplayName("Redirect URI 목록이 비어 있으면 기동 설정 검증에 실패한다")
    void constructor_withoutRedirectUris_throwsIllegalStateException() {
        assertThatThrownBy(() -> properties(REST_API_KEY, CLIENT_SECRET, List.of(), 900))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redirect-uris");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("가입 토큰 TTL이 0 이하면 기동 설정 검증에 실패한다")
    void constructor_withNonPositiveExpiration_throwsIllegalStateException(long expirationSeconds) {
        assertThatThrownBy(
                        () ->
                                properties(
                                        REST_API_KEY,
                                        CLIENT_SECRET,
                                        List.of(REDIRECT_URI),
                                        expirationSeconds))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 이상");
    }

    private KakaoProperties properties(
            String restApiKey,
            String clientSecret,
            List<String> redirectUris,
            long expirationSeconds) {
        return new KakaoProperties(
                restApiKey,
                clientSecret,
                redirectUris,
                expirationSeconds,
                "https://kauth.kakao.com",
                "https://kapi.kakao.com");
    }
}
