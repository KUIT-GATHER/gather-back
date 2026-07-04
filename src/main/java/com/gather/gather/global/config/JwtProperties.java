package com.gather.gather.global.config;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT Access Token 발급/검증에 사용하는 설정. {@code @ConfigurationPropertiesScan}으로 자동 등록된다.
 *
 * <p>secret은 Base64로 인코딩된 문자열이며, 환경별 주입 방식은 아래와 같다.
 *
 * <ul>
 *   <li>로컬: {@code application-secret.yml}의 {@code jwt.secret} (application-secret.yml.example 참고)
 *   <li>운영(EC2): {@code /etc/gather/gather.env}의 {@code JWT_SECRET} (relaxed binding으로 jwt.secret에 매핑)
 * </ul>
 *
 * <p>설정이 비어 있거나 취약하면 애플리케이션이 기동되지 않도록 생성 시점에 검증한다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, @DefaultValue("30") int accessTokenValidityMinutes) {

    // HS256 서명 키의 최소 길이. RFC 7518 기준 HMAC-SHA256 키는 최소 256비트(32바이트) 이상이어야 한다.
    private static final int MIN_SECRET_BYTE_LENGTH = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret이 설정되지 않았습니다. 로컬은 application-secret.yml, 운영은 JWT_SECRET 환경변수를 확인하세요.");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("jwt.secret이 Base64 형식이 아닙니다.", exception);
        }
        if (decoded.length < MIN_SECRET_BYTE_LENGTH) {
            throw new IllegalStateException(
                    "jwt.secret은 Base64 디코딩 후 최소 "
                            + MIN_SECRET_BYTE_LENGTH
                            + "바이트 이상이어야 합니다. 현재 "
                            + decoded.length
                            + "바이트입니다.");
        }
        if (accessTokenValidityMinutes <= 0) {
            throw new IllegalStateException(
                    "jwt.access-token-validity-minutes는 1 이상이어야 합니다. 현재 값: "
                            + accessTokenValidityMinutes);
        }
    }

    /** 기본 record toString()이 secret을 그대로 노출하는 것을 막기 위한 마스킹 오버라이드. */
    @Override
    public String toString() {
        return "JwtProperties[secret=****, accessTokenValidityMinutes="
                + accessTokenValidityMinutes
                + "]";
    }
}
