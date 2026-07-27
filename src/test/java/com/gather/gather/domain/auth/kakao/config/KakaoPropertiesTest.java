package com.gather.gather.domain.auth.kakao.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KakaoPropertiesTest {

    private static final String REST_API_KEY = "test-rest-api-key";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_URI = "https://gathernow.kr/login/kakao/callback";
    private static final String SIGNUP_TOKEN_SECRET =
            Base64.getEncoder().encodeToString(new byte[32]);
    private static final String ADMIN_KEY = "test-kakao-admin-key-0123456789a";
    private static final String APP_ID = "1234567";

    @Test
    @DisplayName("정상 설정은 생성되고 toString에서 비밀값을 마스킹한다")
    void constructor_withValidValues_createsPropertiesAndMasksSecrets() {
        KakaoProperties properties =
                properties(
                        REST_API_KEY,
                        CLIENT_SECRET,
                        List.of(REDIRECT_URI),
                        SIGNUP_TOKEN_SECRET,
                        900);

        assertThat(properties.redirectUris()).containsExactly(REDIRECT_URI);
        assertThat(properties.signupTokenExpirationSeconds()).isEqualTo(900);
        assertThat(properties.toString())
                .doesNotContain(REST_API_KEY, CLIENT_SECRET, SIGNUP_TOKEN_SECRET)
                .contains("restApiKey=****", "clientSecret=****", "signupTokenSecret=****");
    }

    @Test
    @DisplayName("REST API 키가 없으면 기동 설정 검증에 실패한다")
    void constructor_withoutRestApiKey_throwsIllegalStateException() {
        assertThatThrownBy(
                        () ->
                                properties(
                                        " ",
                                        CLIENT_SECRET,
                                        List.of(REDIRECT_URI),
                                        SIGNUP_TOKEN_SECRET,
                                        900))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_REST_API_KEY");
    }

    @Test
    @DisplayName("Client Secret이 없으면 기동 설정 검증에 실패한다")
    void constructor_withoutClientSecret_throwsIllegalStateException() {
        assertThatThrownBy(
                        () ->
                                properties(
                                        REST_API_KEY,
                                        null,
                                        List.of(REDIRECT_URI),
                                        SIGNUP_TOKEN_SECRET,
                                        900))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_CLIENT_SECRET");
    }

    @Test
    @DisplayName("가입 토큰 Secret이 없으면 기동 설정 검증에 실패한다")
    void constructor_withoutSignupTokenSecret_throwsIllegalStateException() {
        assertThatThrownBy(
                        () ->
                                properties(
                                        REST_API_KEY,
                                        CLIENT_SECRET,
                                        List.of(REDIRECT_URI),
                                        "",
                                        900))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_SIGNUP_TOKEN_SECRET");
    }

    @Test
    @DisplayName("가입 토큰 Secret이 Base64가 아니면 기동 설정 검증에 실패한다")
    void constructor_withInvalidBase64Secret_throwsIllegalStateException() {
        assertThatThrownBy(
                        () ->
                                properties(
                                        REST_API_KEY,
                                        CLIENT_SECRET,
                                        List.of(REDIRECT_URI),
                                        "not-base64!",
                                        900))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64 형식");
    }

    @Test
    @DisplayName("가입 토큰 Secret이 32바이트 미만이면 기동 설정 검증에 실패한다")
    void constructor_withShortSecret_throwsIllegalStateException() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(
                        () ->
                                properties(
                                        REST_API_KEY,
                                        CLIENT_SECRET,
                                        List.of(REDIRECT_URI),
                                        shortSecret,
                                        900))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("최소 32바이트");
    }

    @Test
    @DisplayName("Redirect URI 목록이 비어 있으면 기동 설정 검증에 실패한다")
    void constructor_withoutRedirectUris_throwsIllegalStateException() {
        assertThatThrownBy(
                        () ->
                                properties(
                                        REST_API_KEY,
                                        CLIENT_SECRET,
                                        List.of(),
                                        SIGNUP_TOKEN_SECRET,
                                        900))
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
                                        SIGNUP_TOKEN_SECRET,
                                        expirationSeconds))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    @DisplayName("어드민 키가 없으면 기동 설정 검증에 실패한다")
    void constructor_withoutAdminKey_throwsIllegalStateException() {
        assertThatThrownBy(() -> propertiesWith(" ", APP_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_ADMIN_KEY");
    }

    @Test
    @DisplayName("어드민 키가 32자가 아니면 기동 설정 검증에 실패한다 (자리표시자가 운영에 나가는 것을 막는다)")
    void constructor_withWrongLengthAdminKey_throwsIllegalStateException() {
        assertThatThrownBy(() -> propertiesWith("short-admin-key", APP_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32자여야");
        // 길이 하한만 두면 example 파일의 긴 자리표시자가 그대로 통과한다.
        assertThatThrownBy(() -> propertiesWith(ADMIN_KEY + "-extra", APP_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32자여야");
    }

    @Test
    @DisplayName("앱 ID가 없으면 기동 설정 검증에 실패한다")
    void constructor_withoutAppId_throwsIllegalStateException() {
        assertThatThrownBy(() -> propertiesWith(ADMIN_KEY, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KAKAO_APP_ID");
    }

    @Test
    @DisplayName("toString은 어드민 키를 가리고 비밀값이 아닌 앱 ID는 남긴다")
    void toString_masksAdminKeyButKeepsAppId() {
        assertThat(propertiesWith(ADMIN_KEY, APP_ID).toString())
                .doesNotContain(ADMIN_KEY)
                .contains("adminKey=****", "appId=" + APP_ID);
    }

    private KakaoProperties properties(
            String restApiKey,
            String clientSecret,
            List<String> redirectUris,
            String signupTokenSecret,
            long expirationSeconds) {
        return new KakaoProperties(
                restApiKey,
                clientSecret,
                ADMIN_KEY,
                APP_ID,
                redirectUris,
                signupTokenSecret,
                expirationSeconds,
                "https://kauth.kakao.com",
                "https://kapi.kakao.com");
    }

    private KakaoProperties propertiesWith(String adminKey, String appId) {
        return new KakaoProperties(
                REST_API_KEY,
                CLIENT_SECRET,
                adminKey,
                appId,
                List.of(REDIRECT_URI),
                SIGNUP_TOKEN_SECRET,
                900,
                "https://kauth.kakao.com",
                "https://kapi.kakao.com");
    }
}
