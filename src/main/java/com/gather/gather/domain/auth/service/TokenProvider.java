package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserRole;
import com.gather.gather.global.config.JwtProperties;
import com.gather.gather.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class TokenProvider {

    private static final int TOKEN_BYTE_LENGTH = 48;
    private static final int REFRESH_TOKEN_VALID_DAYS = 14;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final String ROLE_CLAIM = "role";

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey accessTokenKey;
    private final long accessTokenValidityMillis;

    public TokenProvider(JwtProperties jwtProperties) {
        this.accessTokenKey =
                Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtProperties.secret()));
        this.accessTokenValidityMillis =
                jwtProperties.accessTokenValidityMinutes() * MILLIS_PER_MINUTE;
    }

    // --- Refresh Token 전용 (랜덤 문자열 발급 + 해시 저장) ---

    public String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public LocalDateTime refreshTokenExpiresAt() {
        return LocalDateTime.now().plusDays(REFRESH_TOKEN_VALID_DAYS);
    }

    // --- Access Token 전용 (HS256 서명 JWT) ---

    /** userId(sub)와 role을 담아 설정된 TTL로 만료되는 HS256 서명 JWT를 발급한다. */
    public String createAccessToken(User user) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + accessTokenValidityMillis);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(accessTokenKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Access Token을 검증하고 userId/role을 추출한다.
     *
     * <p>만료 토큰은 {@link ErrorCode#EXPIRED_TOKEN}, 그 외 모든 무효 토큰(서명 불일치/변조/형식 오류/sub·role 누락 및 변환 불가
     * 등)은 {@link ErrorCode#INVALID_TOKEN}으로 {@link JwtAuthenticationException}을 던진다. JJWT 라이브러리 예외는
     * 이 메서드 밖으로 노출하지 않는다.
     */
    public AccessTokenPayload parseAccessToken(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(accessTokenKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

            String subject = claims.getSubject();
            String roleName = claims.get(ROLE_CLAIM, String.class);
            if (subject == null || roleName == null) {
                throw new JwtAuthenticationException(ErrorCode.INVALID_TOKEN);
            }
            Long userId = Long.parseLong(subject);
            UserRole role = UserRole.valueOf(roleName);
            return new AccessTokenPayload(userId, role);
        } catch (ExpiredJwtException exception) {
            throw new JwtAuthenticationException(ErrorCode.EXPIRED_TOKEN, exception);
        } catch (JwtException | IllegalArgumentException exception) {
            // 서명 불일치/변조/형식 오류, sub의 Long 변환 실패, role의 UserRole 변환 실패 등
            throw new JwtAuthenticationException(ErrorCode.INVALID_TOKEN, exception);
        }
    }
}
