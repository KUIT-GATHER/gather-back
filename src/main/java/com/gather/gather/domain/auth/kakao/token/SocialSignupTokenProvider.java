package com.gather.gather.domain.auth.kakao.token;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.kakao.config.KakaoProperties;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 카카오 인증과 추가정보 제출을 연결하는 가입용 임시 토큰의 발급·검증기.
 *
 * <p>일반 Access Token과 서명 키·발급기·검증기를 분리한다. 키가 다르므로 일반 Access Token을 가입 토큰으로 제출하거나 그 반대로 제출해도 서명 검증에서
 * 걸린다.
 *
 * <p>일회용 처리는 하지 않는다(jti를 저장하지 않음). 재사용은 15분 만료와 {@code (provider, provider_user_key)} 유니크 제약, 가입 직전
 * 재조회로 막는다.
 */
@Component
public class SocialSignupTokenProvider {

    private static final String ISSUER = "gather";
    private static final String AUDIENCE = "gather-social-signup";
    private static final String TOKEN_TYPE = "SOCIAL_SIGNUP";
    private static final String PROVIDER_CLAIM = "provider";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String IDENTIFIER_TYPE_CLAIM = "identifierType";
    private static final String PROVIDER_USER_KEY_CLAIM = "providerUserKey";
    private static final String PROVIDER_USER_KEY_VERSION_CLAIM = "providerUserKeyVersion";
    private static final String PROVIDER_USER_ID_CIPHERTEXT_CLAIM = "providerUserIdCiphertext";
    private static final String ENCRYPTION_KEY_VERSION_CLAIM = "encryptionKeyVersion";
    private static final String SUBJECT_SEPARATOR = ":";
    private static final long MILLIS_PER_SECOND = 1_000L;

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public SocialSignupTokenProvider(KakaoProperties properties) {
        this.signingKey =
                Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.signupTokenSecret()));
        this.expirationSeconds = properties.signupTokenExpirationSeconds();
    }

    public String createSignupToken(
            SocialProvider provider,
            RejoinBlockIdentifier identifier,
            EncryptedProviderUserId encryptedProviderUserId) {
        validateIdentity(provider, identifier, encryptedProviderUserId);
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + expirationSeconds * MILLIS_PER_SECOND);
        return Jwts.builder()
                .issuer(ISSUER)
                .audience()
                .add(AUDIENCE)
                .and()
                .subject(toSubject(provider, identifier.hash()))
                .claim(PROVIDER_CLAIM, provider.name())
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE)
                .claim(IDENTIFIER_TYPE_CLAIM, identifier.type().name())
                .claim(PROVIDER_USER_KEY_CLAIM, identifier.hash())
                .claim(PROVIDER_USER_KEY_VERSION_CLAIM, identifier.keyVersion())
                .claim(PROVIDER_USER_ID_CIPHERTEXT_CLAIM, encryptedProviderUserId.ciphertext())
                .claim(ENCRYPTION_KEY_VERSION_CLAIM, encryptedProviderUserId.keyVersion())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 가입용 토큰을 검증하고 provider와 HMAC·암호문을 추출한다.
     *
     * <p>만료는 {@link ErrorCode#SIGNUP_TOKEN_EXPIRED}, 그 외 모든 무효 토큰(서명 불일치·클레임 불일치·형식 오류)은 {@link
     * ErrorCode#SIGNUP_TOKEN_INVALID}로 던진다. 프론트는 두 경우 모두 signupToken을 지우지만 안내 문구가 다르다.
     */
    public SocialSignupTokenPayload parseSignupToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }

        Claims claims;
        try {
            claims =
                    Jwts.parser()
                            .verifyWith(signingKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }

        try {
            validateClaims(claims);
            SocialProvider provider = parseProvider(claims.get(PROVIDER_CLAIM, String.class));
            RejoinBlockIdentifier identifier = parseIdentifier(claims);
            EncryptedProviderUserId encryptedProviderUserId = parseEncryptedProviderUserId(claims);
            validateSubject(claims, provider, identifier.hash());
            validateIdentity(provider, identifier, encryptedProviderUserId);
            return new SocialSignupTokenPayload(provider, identifier, encryptedProviderUserId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
    }

    private void validateClaims(Claims claims) {
        if (!ISSUER.equals(claims.getIssuer())) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(AUDIENCE)) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
        if (!TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
    }

    private SocialProvider parseProvider(String providerName) {
        if (providerName == null) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
        try {
            return SocialProvider.valueOf(providerName);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
    }

    private RejoinBlockIdentifier parseIdentifier(Claims claims) {
        String identifierTypeName = claims.get(IDENTIFIER_TYPE_CLAIM, String.class);
        String providerUserKey = claims.get(PROVIDER_USER_KEY_CLAIM, String.class);
        int providerUserKeyVersion = parsePositiveVersion(claims, PROVIDER_USER_KEY_VERSION_CLAIM);
        if (identifierTypeName == null || providerUserKey == null || providerUserKey.isBlank()) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
        try {
            return new RejoinBlockIdentifier(
                    AccountRejoinBlockIdentifierType.valueOf(identifierTypeName),
                    providerUserKey,
                    providerUserKeyVersion);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
    }

    private EncryptedProviderUserId parseEncryptedProviderUserId(Claims claims) {
        String ciphertext = claims.get(PROVIDER_USER_ID_CIPHERTEXT_CLAIM, String.class);
        int encryptionKeyVersion = parsePositiveVersion(claims, ENCRYPTION_KEY_VERSION_CLAIM);
        try {
            return new EncryptedProviderUserId(ciphertext, encryptionKeyVersion);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
    }

    private int parsePositiveVersion(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (!(value instanceof Number number)
                || number.longValue() <= 0
                || number.longValue() > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
        return number.intValue();
    }

    private void validateSubject(Claims claims, SocialProvider provider, String providerUserKey) {
        String subject = claims.getSubject();
        String expectedPrefix = subjectPrefix(provider);
        if (subject == null || !subject.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
        if (!subject.substring(expectedPrefix.length()).equals(providerUserKey)) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
    }

    private void validateIdentity(
            SocialProvider provider,
            RejoinBlockIdentifier identifier,
            EncryptedProviderUserId encryptedProviderUserId) {
        if (provider != SocialProvider.KAKAO
                || identifier == null
                || identifier.type() != AccountRejoinBlockIdentifierType.KAKAO
                || identifier.hash() == null
                || identifier.hash().isBlank()
                || identifier.keyVersion() <= 0
                || encryptedProviderUserId == null) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
    }

    private String toSubject(SocialProvider provider, String providerUserKey) {
        return subjectPrefix(provider) + providerUserKey;
    }

    private String subjectPrefix(SocialProvider provider) {
        return provider.name().toLowerCase(Locale.ROOT) + SUBJECT_SEPARATOR;
    }
}
