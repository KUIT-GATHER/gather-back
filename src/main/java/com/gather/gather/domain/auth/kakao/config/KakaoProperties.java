package com.gather.gather.domain.auth.kakao.config;

import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 카카오 로그인 연동 설정. {@code @ConfigurationPropertiesScan}으로 자동 등록된다.
 *
 * <p>비밀값(restApiKey/clientSecret/adminKey/signupTokenSecret)의 환경별 주입 방식은 {@code jwt.secret}과 같다.
 * appId는 비밀값이 아니다.
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
        String adminKey,
        String appId,
        List<String> redirectUris,
        String signupTokenSecret,
        @DefaultValue("900") long signupTokenExpirationSeconds,
        @DefaultValue("https://kauth.kakao.com") String authBaseUrl,
        @DefaultValue("https://kapi.kakao.com") String apiBaseUrl) {

    // 가입 토큰도 HS256으로 서명하므로 jwt.secret과 동일한 최소 길이 요건을 적용한다(RFC 7518).
    private static final int MIN_SECRET_BYTE_LENGTH = 32;

    // 어드민 키는 연결 해제 웹훅의 발신자 검증 기준이기도 하다. 자리표시자가 운영에 나가면 웹훅 인증이 조용히 무력화되므로 길이로 거른다.
    // 카카오디벨로퍼스에서 확인한 대표 어드민 키 길이(2026-07-27). "이상"으로 두면 긴 자리표시자가 그대로 통과한다.
    private static final int ADMIN_KEY_LENGTH = 32;

    public KakaoProperties {
        requireConfigured(restApiKey, "kakao.rest-api-key", "KAKAO_REST_API_KEY");
        // Client Secret이 카카오 콘솔에서 활성화돼 있어 토큰 교환에 필수다. 누락 시 KOE010으로 실패한다.
        requireConfigured(clientSecret, "kakao.client-secret", "KAKAO_CLIENT_SECRET");
        requireConfigured(adminKey, "kakao.admin-key", "KAKAO_ADMIN_KEY");
        validateAdminKey(adminKey);
        requireConfigured(appId, "kakao.app-id", "KAKAO_APP_ID");
        requireConfigured(
                signupTokenSecret, "kakao.signup-token-secret", "KAKAO_SIGNUP_TOKEN_SECRET");
        validateSignupTokenSecret(signupTokenSecret);

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

    private static void validateAdminKey(String adminKey) {
        if (adminKey.length() != ADMIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "kakao.admin-key는 "
                            + ADMIN_KEY_LENGTH
                            + "자여야 합니다. 현재 "
                            + adminKey.length()
                            + "자입니다. 로컬은 임의의 더미 값이어도 되지만 길이는 맞춰야 합니다.");
        }
    }

    private static void validateSignupTokenSecret(String secret) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "kakao.signup-token-secret이 Base64 형식이 아닙니다.", exception);
        }
        if (decoded.length < MIN_SECRET_BYTE_LENGTH) {
            throw new IllegalStateException(
                    "kakao.signup-token-secret은 Base64 디코딩 후 최소 "
                            + MIN_SECRET_BYTE_LENGTH
                            + "바이트 이상이어야 합니다. 현재 "
                            + decoded.length
                            + "바이트입니다.");
        }
    }

    /** 기본 record toString()이 비밀값을 그대로 노출하는 것을 막기 위한 마스킹 오버라이드. */
    @Override
    public String toString() {
        return "KakaoProperties[restApiKey=****, clientSecret=****, adminKey=****,"
                + " signupTokenSecret=****, appId="
                + appId
                + ", redirectUris="
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
