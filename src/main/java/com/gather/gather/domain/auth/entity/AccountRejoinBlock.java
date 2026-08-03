package com.gather.gather.domain.auth.entity;

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
        name = "account_rejoin_block",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_account_rejoin_block_identifier",
                        columnNames = {"identifier_type", "identifier_hash"}),
        indexes = @Index(name = "idx_account_rejoin_block_expires_at", columnList = "expires_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRejoinBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountRejoinBlockIdentifierType identifierType;

    @Column(nullable = false, length = 64)
    private String identifierHash;

    @Column(nullable = false)
    private int keyVersion;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Long sourceUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private AccountRejoinBlock(
            AccountRejoinBlockIdentifierType identifierType,
            String identifierHash,
            int keyVersion,
            LocalDateTime expiresAt,
            Long sourceUserId,
            LocalDateTime createdAt) {
        if (identifierType == null
                || identifierHash == null
                || identifierHash.isBlank()
                || expiresAt == null
                || sourceUserId == null
                || createdAt == null) {
            throw new IllegalArgumentException("재가입 제한 필수 값이 누락되었습니다.");
        }
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("재가입 제한 키 버전은 1 이상이어야 합니다.");
        }
        this.identifierType = identifierType;
        this.identifierHash = identifierHash;
        this.keyVersion = keyVersion;
        this.expiresAt = expiresAt;
        this.sourceUserId = sourceUserId;
        this.createdAt = createdAt;
    }

    public static AccountRejoinBlock create(
            AccountRejoinBlockIdentifierType identifierType,
            String identifierHash,
            int keyVersion,
            LocalDateTime expiresAt,
            Long sourceUserId,
            LocalDateTime createdAt) {
        return new AccountRejoinBlock(
                identifierType, identifierHash, keyVersion, expiresAt, sourceUserId, createdAt);
    }

    public boolean isActiveAt(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("재가입 제한 확인 시각은 필수입니다.");
        }
        return now.isBefore(expiresAt);
    }

    public void extendUntil(LocalDateTime newExpiresAt) {
        if (newExpiresAt == null) {
            throw new IllegalArgumentException("새 만료 시각은 필수입니다.");
        }
        if (newExpiresAt.isAfter(expiresAt)) {
            this.expiresAt = newExpiresAt;
        }
    }
}
