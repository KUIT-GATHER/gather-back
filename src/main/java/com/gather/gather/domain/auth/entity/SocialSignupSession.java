package com.gather.gather.domain.auth.entity;

import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "social_signup_session",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_social_signup_session_token_hash",
                        columnNames = "token_hash"),
        indexes = {
            @Index(
                    name = "idx_social_signup_session_provider_key_status",
                    columnList = "provider, provider_user_key, status"),
            @Index(name = "idx_social_signup_session_expires_at", columnList = "expires_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialSignupSession {

    private static final int SHA_256_HEX_LENGTH = 64;
    private static final int PROVIDER_USER_KEY_LENGTH = 64;
    private static final int PROVIDER_USER_ID_CIPHERTEXT_MAX_LENGTH = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = SHA_256_HEX_LENGTH)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_key", nullable = false, length = 64)
    private String providerUserKey;

    @Column(name = "provider_user_key_version", nullable = false)
    private int providerUserKeyVersion;

    @Column(name = "provider_user_id_ciphertext", nullable = false, length = 512)
    private String providerUserIdCiphertext;

    @Column(name = "encryption_key_version", nullable = false)
    private int encryptionKeyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialSignupSessionStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column private LocalDateTime consumedAt;

    @Column private LocalDateTime cancelledAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private SocialSignupSession(
            String tokenHash,
            RejoinBlockIdentifier identifier,
            EncryptedProviderUserId encryptedProviderUserId,
            LocalDateTime expiresAt,
            LocalDateTime now) {
        validateCreation(tokenHash, identifier, encryptedProviderUserId, expiresAt, now);
        this.tokenHash = tokenHash;
        this.provider = SocialProvider.KAKAO;
        this.providerUserKey = identifier.hash();
        this.providerUserKeyVersion = identifier.keyVersion();
        this.providerUserIdCiphertext = encryptedProviderUserId.ciphertext();
        this.encryptionKeyVersion = encryptedProviderUserId.keyVersion();
        this.status = SocialSignupSessionStatus.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SocialSignupSession createKakao(
            String tokenHash,
            RejoinBlockIdentifier identifier,
            EncryptedProviderUserId encryptedProviderUserId,
            LocalDateTime expiresAt,
            LocalDateTime now) {
        return new SocialSignupSession(
                tokenHash, identifier, encryptedProviderUserId, expiresAt, now);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        requireNow(now);
        return !expiresAt.isAfter(now);
    }

    public void consume(LocalDateTime now) {
        requireNow(now);
        requireStatus(SocialSignupSessionStatus.PENDING, "대기 중인 가입 세션만 소비할 수 있습니다.");
        requireNotBeforeCreatedAt(now);
        if (isExpiredAt(now)) {
            throw new IllegalStateException("만료된 가입 세션은 소비할 수 없습니다.");
        }
        this.status = SocialSignupSessionStatus.CONSUMED;
        this.consumedAt = now;
        this.updatedAt = now;
    }

    public void cancel(LocalDateTime now) {
        requireNow(now);
        if (status == SocialSignupSessionStatus.CANCELLED) {
            return;
        }
        requireStatus(SocialSignupSessionStatus.PENDING, "소비된 가입 세션은 취소할 수 없습니다.");
        requireNotBeforeCreatedAt(now);
        this.status = SocialSignupSessionStatus.CANCELLED;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    public EncryptedProviderUserId encryptedProviderUserId() {
        return new EncryptedProviderUserId(providerUserIdCiphertext, encryptionKeyVersion);
    }

    private static void validateCreation(
            String tokenHash,
            RejoinBlockIdentifier identifier,
            EncryptedProviderUserId encryptedProviderUserId,
            LocalDateTime expiresAt,
            LocalDateTime now) {
        if (tokenHash == null
                || tokenHash.length() != SHA_256_HEX_LENGTH
                || !isLowercaseHex(tokenHash)) {
            throw new IllegalArgumentException("가입 세션 토큰 hash 형식이 올바르지 않습니다.");
        }
        String providerUserKey = identifier == null ? null : identifier.hash();
        if (providerUserKey == null
                || providerUserKey.length() != PROVIDER_USER_KEY_LENGTH
                || !isLowercaseHex(providerUserKey)) {
            throw new IllegalArgumentException("소셜 계정 조회 키 형식이 올바르지 않습니다.");
        }
        if (identifier.keyVersion() <= 0) {
            throw new IllegalArgumentException("소셜 계정 조회 키 버전은 1 이상이어야 합니다.");
        }
        if (encryptedProviderUserId == null) {
            throw new IllegalArgumentException("암호화된 소셜 식별자는 필수입니다.");
        }
        if (encryptedProviderUserId.ciphertext().length()
                > PROVIDER_USER_ID_CIPHERTEXT_MAX_LENGTH) {
            throw new IllegalArgumentException("암호화된 소셜 식별자가 허용 길이를 초과했습니다.");
        }
        if (expiresAt == null || now == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("가입 세션 만료 시각은 생성 시각보다 이후여야 합니다.");
        }
    }

    private static boolean isLowercaseHex(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character < '0' || character > '9') && (character < 'a' || character > 'f')) {
                return false;
            }
        }
        return true;
    }

    private void requireStatus(SocialSignupSessionStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireNow(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("가입 세션 상태 확인 시각은 필수입니다.");
        }
    }

    private void requireNotBeforeCreatedAt(LocalDateTime now) {
        if (now.isBefore(createdAt)) {
            throw new IllegalArgumentException("가입 세션 상태 변경 시각은 생성 시각보다 빠를 수 없습니다.");
        }
    }
}
