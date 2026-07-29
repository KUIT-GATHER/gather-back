package com.gather.gather.domain.auth.kakao.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SocialSignupTokenProviderTest {

    private static final String SIGNUP_TOKEN_SECRET =
            "z9tOf6reUdkTRI0KFFiydLKdxpayBBxVWSAm7EJTgKXolFCFvnQ4qViBrdh6y7yP";
    // test application.yml의 jwt.secret과 같은 값. 일반 Access Token 키로 서명한 토큰이 거부되는지 확인하는 데 쓴다.
    private static final String ACCESS_TOKEN_SECRET =
            "XZWyFEbfHyT37TkUd6Z63CN9wJbT8vlWdmQSzoIZqqGOnAj4ezamA4BO/tChr4bmeE0bbSExFfD8lN/BLitbuQ==";
    private static final String ISSUER = "gather";
    private static final String AUDIENCE = "gather-social-signup";
    private static final String PROVIDER_USER_ID = "123456789";
    private static final String PROVIDER_USER_KEY = "a".repeat(64);
    private static final long TTL_SECONDS = 900;
    private static final RejoinBlockIdentifier IDENTIFIER =
            new RejoinBlockIdentifier(AccountRejoinBlockIdentifierType.KAKAO, PROVIDER_USER_KEY, 1);
    private static final EncryptedProviderUserId ENCRYPTED_PROVIDER_USER_ID =
            new EncryptedProviderUserId("encrypted-provider-user-id", 2);

    private final SocialSignupTokenProvider provider = new SocialSignupTokenProvider(properties());
    private final SecretKey signingKey = key(SIGNUP_TOKEN_SECRET);

    private KakaoProperties properties() {
        return new KakaoProperties(
                "test-rest-api-key",
                "test-client-secret",
                List.of("https://gathernow.kr/login/kakao/callback"),
                SIGNUP_TOKEN_SECRET,
                TTL_SECONDS,
                "https://kauth.kakao.com",
                "https://kapi.kakao.com");
    }

    @Test
    @DisplayName("발급한 토큰은 provider와 HMAC·암호문으로 되돌아온다")
    void createAndParse_roundTrips() {
        String token =
                provider.createSignupToken(
                        SocialProvider.KAKAO, IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID);

        SocialSignupTokenPayload payload = provider.parseSignupToken(token);

        assertThat(payload.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(payload.identifier()).isEqualTo(IDENTIFIER);
        assertThat(payload.encryptedProviderUserId()).isEqualTo(ENCRYPTED_PROVIDER_USER_ID);
    }

    @Test
    @DisplayName("가입 토큰의 만료시간은 15분이며 클레임은 설계대로 채워진다")
    void createSignupToken_expiresIn15MinutesWithDesignedClaims() {
        String token =
                provider.createSignupToken(
                        SocialProvider.KAKAO, IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID);

        Claims claims =
                Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();

        long ttlSeconds =
                (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(ttlSeconds).isEqualTo(TTL_SECONDS);
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).containsExactly(AUDIENCE);
        assertThat(claims.getSubject()).isEqualTo("kakao:" + PROVIDER_USER_KEY);
        assertThat(claims.get("provider", String.class)).isEqualTo("KAKAO");
        assertThat(claims.get("tokenType", String.class)).isEqualTo("SOCIAL_SIGNUP");
        assertThat(claims.get("identifierType", String.class)).isEqualTo("KAKAO");
        assertThat(claims.get("providerUserKey", String.class)).isEqualTo(PROVIDER_USER_KEY);
        assertThat(claims.get("providerUserKeyVersion", Integer.class)).isEqualTo(1);
        assertThat(claims.get("providerUserIdCiphertext", String.class))
                .isEqualTo(ENCRYPTED_PROVIDER_USER_ID.ciphertext());
        assertThat(claims.get("encryptionKeyVersion", Integer.class)).isEqualTo(2);
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    @DisplayName("가입 토큰에는 평문 카카오 회원번호와 프로필 개인정보를 넣지 않는다")
    void createSignupToken_containsOnlyIdentityClaims() {
        String token =
                provider.createSignupToken(
                        SocialProvider.KAKAO, IDENTIFIER, ENCRYPTED_PROVIDER_USER_ID);

        Claims claims =
                Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();

        assertThat(claims.keySet())
                .containsExactlyInAnyOrder(
                        "iss",
                        "aud",
                        "sub",
                        "provider",
                        "tokenType",
                        "identifierType",
                        "providerUserKey",
                        "providerUserKeyVersion",
                        "providerUserIdCiphertext",
                        "encryptionKeyVersion",
                        "jti",
                        "iat",
                        "exp");
        assertThat(claims.values()).doesNotContain(PROVIDER_USER_ID);
    }

    @Test
    @DisplayName("만료된 가입 토큰은 SIGNUP_TOKEN_EXPIRED로 거부한다")
    void parse_expiredToken_throwsSignupTokenExpired() {
        long now = System.currentTimeMillis();
        String expired =
                validClaims()
                        .issuedAt(new Date(now - 1_000_000))
                        .expiration(new Date(now - 100_000))
                        .compact();

        assertErrorCode(expired, ErrorCode.SIGNUP_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 SIGNUP_TOKEN_INVALID로 거부한다")
    void parse_tamperedSignature_throwsSignupTokenInvalid() {
        String forged = validClaims().signWith(key(ACCESS_TOKEN_SECRET), Jwts.SIG.HS256).compact();

        assertErrorCode(forged, ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("일반 Access Token을 가입 토큰으로 제출하면 거부한다")
    void parse_accessToken_throwsSignupTokenInvalid() {
        long now = System.currentTimeMillis();
        String accessToken =
                Jwts.builder()
                        .subject("1")
                        .claim("role", "USER")
                        .issuedAt(new Date(now))
                        .expiration(new Date(now + 1_800_000))
                        .signWith(key(ACCESS_TOKEN_SECRET), Jwts.SIG.HS256)
                        .compact();

        assertErrorCode(accessToken, ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("issuer가 다른 토큰은 거부한다")
    void parse_wrongIssuer_throwsSignupTokenInvalid() {
        assertErrorCode(
                baseClaims().issuer("evil").audience().add(AUDIENCE).and().compact(),
                ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("audience가 다른 토큰은 거부한다")
    void parse_wrongAudience_throwsSignupTokenInvalid() {
        assertErrorCode(
                baseClaims().issuer(ISSUER).audience().add("gather-access").and().compact(),
                ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("tokenType이 SOCIAL_SIGNUP이 아닌 토큰은 거부한다")
    void parse_wrongTokenType_throwsSignupTokenInvalid() {
        assertErrorCode(
                validClaims().claim("tokenType", "ACCESS").compact(),
                ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("지원하지 않는 provider의 토큰은 거부한다")
    void parse_unknownProvider_throwsSignupTokenInvalid() {
        assertErrorCode(
                validClaims()
                        .claim("provider", "NAVER")
                        .subject("naver:" + PROVIDER_USER_KEY)
                        .compact(),
                ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("subject 형식이 provider 접두사와 맞지 않으면 거부한다")
    void parse_subjectWithoutProviderPrefix_throwsSignupTokenInvalid() {
        assertErrorCode(
                validClaims().subject(PROVIDER_USER_KEY).compact(), ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @Test
    @DisplayName("provider user key가 비어 있는 토큰은 거부한다")
    void parse_blankProviderUserKey_throwsSignupTokenInvalid() {
        assertErrorCode(
                validClaims().subject("kakao:").claim("providerUserKey", "").compact(),
                ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-a-jwt"})
    @DisplayName("토큰이 없거나 JWT 형식이 아니면 거부한다")
    void parse_missingOrMalformedToken_throwsSignupTokenInvalid(String token) {
        assertErrorCode(token, ErrorCode.SIGNUP_TOKEN_INVALID);
    }

    private JwtBuilder validClaims() {
        return baseClaims().issuer(ISSUER).audience().add(AUDIENCE).and();
    }

    private JwtBuilder baseClaims() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("kakao:" + PROVIDER_USER_KEY)
                .claim("provider", "KAKAO")
                .claim("tokenType", "SOCIAL_SIGNUP")
                .claim("identifierType", "KAKAO")
                .claim("providerUserKey", PROVIDER_USER_KEY)
                .claim("providerUserKeyVersion", 1)
                .claim("providerUserIdCiphertext", ENCRYPTED_PROVIDER_USER_ID.ciphertext())
                .claim("encryptionKeyVersion", ENCRYPTED_PROVIDER_USER_ID.keyVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + TTL_SECONDS * 1000))
                .signWith(signingKey, Jwts.SIG.HS256);
    }

    private SecretKey key(String base64Secret) {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
    }

    private void assertErrorCode(String token, ErrorCode expected) {
        assertThatThrownBy(() -> provider.parseSignupToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
