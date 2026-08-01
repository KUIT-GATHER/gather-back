package com.gather.gather.domain.auth.kakao.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 카카오 로그인 연동 설정. {@code @ConfigurationPropertiesScan}으로 자동 등록된다.
 *
 * <p>비밀값(restApiKey/clientSecret)의 환경별 주입 방식은 {@code jwt.secret}과 같다.
 *
 * <ul>
 *   <li>로컬: {@code application-secret.yml} (application-secret.yml.example 참고)
 *   <li>운영(EC2): {@code /etc/gather/gather.env}의 {@code KAKAO_REST_API_KEY} 등 (relaxed binding)
 * </ul>
 *
 * <p>설정이 비어 있으면 카카오 API 호출이 런타임에 KOE010 등으로 실패하므로, 기동 시점에 검증해 빠르게 실패시킨다.
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String restApiKey,
        String clientSecret,
        List<String> redirectUris,
        @DefaultValue("900") long signupTokenExpirationSeconds,
        @DefaultValue("https://kauth.kakao.com") String authBaseUrl,
        @DefaultValue("https://kapi.kakao.com") String apiBaseUrl) {

    public KakaoProperties {
        requireConfigured(restApiKey, "kakao.rest-api-key", "KAKAO_REST_API_KEY");
        // Client Secret이 카카오 콘솔에서 활성화돼 있어 토큰 교환에 필수다. 누락 시 KOE010으로 실패한다.
        requireConfigured(clientSecret, "kakao.client-secret", "KAKAO_CLIENT_SECRET");
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new IllegalStateException(
                    "kakao.redirect-uris가 설정되지 않았습니다. 카카오 콘솔에 등록한 Redirect URI와 정확히 같은 값이어야 합니다.");
        }
        redirectUris = List.copyOf(redirectUris);

        if (signupTokenExpirationSeconds <= 0) {
            throw new IllegalStateException(
                    "kakao.signup-token-expiration-seconds는 1 이상이어야 합니다. 현재 값: "
                            + signupTokenExpirationSeconds);
        }
    }

    private static void requireConfigured(
            String value, String property, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    property
                            + "이(가) 설정되지 않았습니다. 로컬은 application-secret.yml, 운영은 "
                            + environmentVariable
                            + " 환경변수를 확인하세요.");
        }
    }

    /** 기본 record toString()이 비밀값을 그대로 노출하는 것을 막기 위한 마스킹 오버라이드. */
    @Override
    public String toString() {
        return "KakaoProperties[restApiKey=****, clientSecret=****,"
                + " redirectUris="
                + redirectUris
                + ", signupTokenExpirationSeconds="
                + signupTokenExpirationSeconds
                + ", authBaseUrl="
                + authBaseUrl
                + ", apiBaseUrl="
                + apiBaseUrl
                + "]";
    }
}
