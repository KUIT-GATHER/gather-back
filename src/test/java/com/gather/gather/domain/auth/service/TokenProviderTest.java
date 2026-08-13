package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserRole;
import com.gather.gather.global.config.JwtProperties;
import com.gather.gather.global.exception.ErrorCode;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TokenProviderTest {

    // 테스트 전용 더미 시크릿(운영 값 아님). Base64 디코딩 시 64바이트.
    private static final String SECRET =
            "XZWyFEbfHyT37TkUd6Z63CN9wJbT8vlWdmQSzoIZqqGOnAj4ezamA4BO/tChr4bmeE0bbSExFfD8lN/BLitbuQ==";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-09T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private final TokenProvider tokenProvider =
            new TokenProvider(new JwtProperties(SECRET, 30), FIXED_CLOCK);
    private final SecretKey signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));

    @Test
    @DisplayName("Refresh Token 만료 시각은 주입된 Clock 기준 14일 후이다")
    void refreshTokenExpiresAt_usesInjectedClock() {
        assertThat(tokenProvider.refreshTokenExpiresAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 23, 12, 0));
    }

    @Test
    @DisplayName("발급한 Access Token을 파싱하면 userId와 role이 일치한다")
    void createAndParse_roundTrip() {
        User user = newUser(42L, UserRole.ADMIN);

        String token = tokenProvider.createAccessToken(user);
        AccessTokenPayload payload = tokenProvider.parseAccessToken(token);

        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("만료된 토큰은 EXPIRED_TOKEN으로 구분한다")
    void parse_expiredToken_throwsExpired() {
        Date now = new Date();
        Date past = new Date(now.getTime() - 60_000L);
        String expired =
                Jwts.builder()
                        .subject("1")
                        .claim("role", "USER")
                        .issuedAt(new Date(past.getTime() - 60_000L))
                        .expiration(past)
                        .signWith(signingKey, Jwts.SIG.HS256)
                        .compact();

        assertErrorCode(expired, ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 INVALID_TOKEN이다")
    void parse_wrongKey_throwsInvalid() {
        SecretKey otherKey = Jwts.SIG.HS256.key().build();
        String token =
                Jwts.builder()
                        .subject("1")
                        .claim("role", "USER")
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + 60_000L))
                        .signWith(otherKey, Jwts.SIG.HS256)
                        .compact();

        assertErrorCode(token, ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("변조된 토큰은 INVALID_TOKEN이다")
    void parse_tamperedToken_throwsInvalid() {
        String token = tokenProvider.createAccessToken(newUser(1L, UserRole.USER));

        assertErrorCode(tamperSignature(token), ErrorCode.INVALID_TOKEN);
    }

    /**
     * 서명(세 번째 세그먼트)의 첫 글자를 바꿔 변조한다. Base64 마지막 글자는 디코딩 시 버려지는 비트가 있어 바꿔도 동일한 서명이 될 수 있으므로(간헐적 테스트
     * 실패), 항상 유효 비트인 첫 글자를 바꾼다.
     */
    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);
        return String.join(".", parts);
    }

    @Test
    @DisplayName("형식이 잘못된 토큰은 INVALID_TOKEN이다")
    void parse_malformedToken_throwsInvalid() {
        assertErrorCode("not.a.jwt", ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("sub가 없는 토큰은 INVALID_TOKEN이다")
    void parse_missingSubject_throwsInvalid() {
        assertErrorCode(
                signedToken(builder -> builder.claim("role", "USER")), ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("sub를 Long으로 변환할 수 없으면 INVALID_TOKEN이다")
    void parse_nonNumericSubject_throwsInvalid() {
        assertErrorCode(
                signedToken(builder -> builder.subject("abc").claim("role", "USER")),
                ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("role이 없는 토큰은 INVALID_TOKEN이다")
    void parse_missingRole_throwsInvalid() {
        assertErrorCode(signedToken(builder -> builder.subject("1")), ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("role을 UserRole로 변환할 수 없으면 INVALID_TOKEN이다")
    void parse_invalidRole_throwsInvalid() {
        assertErrorCode(
                signedToken(builder -> builder.subject("1").claim("role", "SUPERUSER")),
                ErrorCode.INVALID_TOKEN);
    }

    private void assertErrorCode(String token, ErrorCode expected) {
        assertThatThrownBy(() -> tokenProvider.parseAccessToken(token))
                .isInstanceOfSatisfying(
                        JwtAuthenticationException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    /** 유효 서명 + 미래 만료를 기본값으로 두고, claim만 커스터마이즈해 토큰을 만든다. */
    private String signedToken(java.util.function.Consumer<JwtBuilder> customizer) {
        Date now = new Date();
        JwtBuilder builder =
                Jwts.builder().issuedAt(now).expiration(new Date(now.getTime() + 60_000L));
        customizer.accept(builder);
        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    private static User newUser(Long id, UserRole role) {
        try {
            var constructor = User.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            User user = constructor.newInstance();
            ReflectionTestUtils.setField(user, "id", id);
            ReflectionTestUtils.setField(user, "role", role);
            return user;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
