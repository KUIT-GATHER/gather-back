package com.gather.gather.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "social_account",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_social_account_provider_key",
                    columnNames = {"provider", "provider_user_key"}),
            @UniqueConstraint(
                    name = "uk_social_account_user_provider",
                    columnNames = {"user_id", "provider"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    // 단계적 migration 동안만 유지한다. 외부에 평문을 노출하지 않도록 getter를 제공하지 않는다.
    @Column(name = "provider_user_id", nullable = false, length = 100)
    @Getter(AccessLevel.NONE)
    private String legacyProviderUserId;

    @Column(name = "provider_user_key", length = 64)
    private String providerUserKey;

    @Column(name = "provider_user_key_version")
    private Integer providerUserKeyVersion;

    @Column(name = "provider_user_id_ciphertext", length = 512)
    private String providerUserIdCiphertext;

    @Column(name = "encryption_key_version")
    private Integer encryptionKeyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", length = 20)
    private SocialAccountLinkStatus linkStatus;

    @Column private Long generation;

    @Column private LocalDateTime connectedAt;

    @Column private LocalDateTime unlinkedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private SocialAccount(
            User user,
            SocialProvider provider,
            String legacyProviderUserId,
            String providerUserKey,
            int providerUserKeyVersion,
            EncryptedProviderUserId encryptedProviderUserId,
            LocalDateTime now) {
        validateLinkedCreation(
                user,
                provider,
                legacyProviderUserId,
                providerUserKey,
                providerUserKeyVersion,
                encryptedProviderUserId,
                now);
        this.user = user;
        this.provider = provider;
        this.legacyProviderUserId = legacyProviderUserId;
        this.providerUserKey = providerUserKey;
        this.providerUserKeyVersion = providerUserKeyVersion;
        this.providerUserIdCiphertext = encryptedProviderUserId.ciphertext();
        this.encryptionKeyVersion = encryptedProviderUserId.keyVersion();
        this.linkStatus = SocialAccountLinkStatus.LINKED;
        this.generation = 1L;
        this.connectedAt = now;
        this.unlinkedAt = null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SocialAccount createLinked(
            User user,
            SocialProvider provider,
            String legacyProviderUserId,
            String providerUserKey,
            int providerUserKeyVersion,
            EncryptedProviderUserId encryptedProviderUserId,
            LocalDateTime now) {
        return new SocialAccount(
                user,
                provider,
                legacyProviderUserId,
                providerUserKey,
                providerUserKeyVersion,
                encryptedProviderUserId,
                now);
    }

    public void backfillLegacyIdentity(
            String providerUserKey,
            int providerUserKeyVersion,
            EncryptedProviderUserId encryptedProviderUserId,
            LocalDateTime now) {
        if (!requiresLegacyIdentityBackfill()) {
            throw new IllegalStateException("이미 새 소셜 계정 식별자 구조로 전환되었습니다.");
        }
        validateIdentity(providerUserKey, providerUserKeyVersion, encryptedProviderUserId, now);
        this.providerUserKey = providerUserKey;
        this.providerUserKeyVersion = providerUserKeyVersion;
        this.providerUserIdCiphertext = encryptedProviderUserId.ciphertext();
        this.encryptionKeyVersion = encryptedProviderUserId.keyVersion();
        this.linkStatus = SocialAccountLinkStatus.LINKED;
        this.generation = 1L;
        this.connectedAt = createdAt;
        this.unlinkedAt = null;
        this.updatedAt = now;
    }

    public void markUnlinkPending(LocalDateTime now) {
        requireStatus(SocialAccountLinkStatus.LINKED, "연결된 소셜 계정만 연결 해제 대기 상태로 전환할 수 있습니다.");
        requireNow(now);
        this.linkStatus = SocialAccountLinkStatus.UNLINK_PENDING;
        this.updatedAt = now;
    }

    public void markUnlinked(LocalDateTime now) {
        requireStatus(SocialAccountLinkStatus.UNLINK_PENDING, "연결 해제 대기 상태의 소셜 계정만 연결 해제할 수 있습니다.");
        requireNow(now);
        this.linkStatus = SocialAccountLinkStatus.UNLINKED;
        this.unlinkedAt = now;
        this.updatedAt = now;
    }

    public void relink(
            User newUser, EncryptedProviderUserId encryptedProviderUserId, LocalDateTime now) {
        requireStatus(SocialAccountLinkStatus.UNLINKED, "연결 해제된 소셜 계정만 새로운 사용자에게 다시 연결할 수 있습니다.");
        if (newUser == null || newUser.getId() == null) {
            throw new IllegalArgumentException("재연결할 영속 사용자는 필수입니다.");
        }
        if (user != null && user.getId() != null && user.getId().equals(newUser.getId())) {
            throw new IllegalStateException("동일한 사용자에게 다시 연결할 수 없습니다.");
        }
        if (encryptedProviderUserId == null) {
            throw new IllegalArgumentException("암호화된 소셜 식별자는 필수입니다.");
        }
        requireNow(now);

        this.user = newUser;
        this.providerUserIdCiphertext = encryptedProviderUserId.ciphertext();
        this.encryptionKeyVersion = encryptedProviderUserId.keyVersion();
        this.linkStatus = SocialAccountLinkStatus.LINKED;
        this.generation = Math.addExact(requireGeneration(), 1L);
        this.connectedAt = now;
        this.unlinkedAt = null;
        this.updatedAt = now;
    }

    public boolean isLinked() {
        return linkStatus == SocialAccountLinkStatus.LINKED;
    }

    public boolean matchesGeneration(long expectedGeneration) {
        return generation != null && generation == expectedGeneration;
    }

    public boolean matchesProviderUserKey(String key, int keyVersion) {
        return providerUserKey != null
                && providerUserKey.equals(key)
                && providerUserKeyVersion != null
                && providerUserKeyVersion == keyVersion;
    }

    public boolean requiresLegacyIdentityBackfill() {
        boolean allMissing =
                providerUserKey == null
                        && providerUserKeyVersion == null
                        && providerUserIdCiphertext == null
                        && encryptionKeyVersion == null
                        && linkStatus == null
                        && generation == null
                        && connectedAt == null
                        && unlinkedAt == null;
        boolean allPresent =
                providerUserKey != null
                        && providerUserKeyVersion != null
                        && providerUserIdCiphertext != null
                        && encryptionKeyVersion != null
                        && linkStatus != null
                        && generation != null
                        && connectedAt != null;
        if (!allMissing && !allPresent) {
            throw new IllegalStateException("소셜 계정 생명주기 데이터가 부분적으로만 저장되어 있습니다.");
        }
        return allMissing;
    }

    private static void validateLinkedCreation(
            User user,
            SocialProvider provider,
            String legacyProviderUserId,
            String providerUserKey,
            int providerUserKeyVersion,
            EncryptedProviderUserId encryptedProviderUserId,
            LocalDateTime now) {
        if (user == null || provider == null) {
            throw new IllegalArgumentException("사용자와 소셜 제공자는 필수입니다.");
        }
        if (legacyProviderUserId == null || legacyProviderUserId.isBlank()) {
            throw new IllegalArgumentException("단계적 전환용 소셜 식별자는 필수입니다.");
        }
        validateIdentity(providerUserKey, providerUserKeyVersion, encryptedProviderUserId, now);
    }

    private static void validateIdentity(
            String providerUserKey,
            int providerUserKeyVersion,
            EncryptedProviderUserId encryptedProviderUserId,
            LocalDateTime now) {
        if (providerUserKey == null || providerUserKey.isBlank()) {
            throw new IllegalArgumentException("소셜 계정 조회 키는 필수입니다.");
        }
        if (providerUserKeyVersion <= 0) {
            throw new IllegalArgumentException("소셜 계정 조회 키 버전은 1 이상이어야 합니다.");
        }
        if (encryptedProviderUserId == null) {
            throw new IllegalArgumentException("암호화된 소셜 식별자는 필수입니다.");
        }
        requireNow(now);
    }

    private void requireStatus(SocialAccountLinkStatus expected, String message) {
        if (linkStatus != expected) {
            throw new IllegalStateException(message);
        }
    }

    private long requireGeneration() {
        if (generation == null || generation <= 0) {
            throw new IllegalStateException("소셜 계정 연결 세대가 올바르지 않습니다.");
        }
        return generation;
    }

    private static void requireNow(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("상태 변경 시각은 필수입니다.");
        }
    }
}
