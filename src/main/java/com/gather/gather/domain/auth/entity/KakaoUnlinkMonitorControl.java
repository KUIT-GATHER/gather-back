package com.gather.gather.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "kakao_unlink_monitor_control")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoUnlinkMonitorControl {

    public static final long SINGLETON_ID = 1L;

    @Id private Long id;

    @Column(nullable = false)
    private long scanSequence;

    @Column(length = 64)
    private String leaseToken;

    @Column(length = 128)
    private String leaseOwner;

    @Column private LocalDateTime leaseAcquiredAt;

    @Column private LocalDateTime leaseExpiresAt;

    @Column private LocalDateTime lastScanStartedAt;

    @Column private LocalDateTime lastScanCompletedAt;

    @Column private LocalDateTime lastScanFailedAt;

    @Column(length = 80)
    private String lastScanFailureType;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public boolean hasValidLease(LocalDateTime now) {
        requireNow(now);
        return leaseToken != null && leaseExpiresAt != null && leaseExpiresAt.isAfter(now);
    }

    public long acquire(
            String owner, String token, LocalDateTime acquiredAt, LocalDateTime expiresAt) {
        requireText(owner, "monitor lease owner", 128);
        requireText(token, "monitor lease token", 64);
        requireNow(acquiredAt);
        requireNow(expiresAt);
        if (hasValidLease(acquiredAt)) {
            throw new IllegalStateException("유효한 monitor lease가 이미 존재합니다.");
        }
        if (!expiresAt.isAfter(acquiredAt)) {
            throw new IllegalArgumentException("monitor lease 만료 시각은 획득 시각보다 늦어야 합니다.");
        }
        scanSequence = Math.addExact(scanSequence, 1);
        leaseToken = token;
        leaseOwner = owner;
        leaseAcquiredAt = acquiredAt;
        leaseExpiresAt = expiresAt;
        lastScanStartedAt = acquiredAt;
        updatedAt = acquiredAt;
        return scanSequence;
    }

    public boolean complete(
            long expectedSequence, String expectedOwner, String expectedToken, LocalDateTime now) {
        requireNow(now);
        if (!hasOwnedValidLease(expectedSequence, expectedOwner, expectedToken, now)) {
            return false;
        }
        lastScanCompletedAt = now;
        updatedAt = now;
        clearLease();
        return true;
    }

    public boolean fail(
            long expectedSequence,
            String expectedOwner,
            String expectedToken,
            String failureType,
            LocalDateTime now) {
        requireNow(now);
        requireText(failureType, "monitor failure type", 80);
        if (!hasOwnedValidLease(expectedSequence, expectedOwner, expectedToken, now)) {
            return false;
        }
        lastScanFailedAt = now;
        lastScanFailureType = failureType;
        updatedAt = now;
        clearLease();
        return true;
    }

    public boolean hasOwnedValidLease(
            long expectedSequence, String expectedOwner, String expectedToken, LocalDateTime now) {
        requireNow(now);
        return scanSequence == expectedSequence
                && leaseOwner != null
                && leaseOwner.equals(expectedOwner)
                && leaseToken != null
                && leaseToken.equals(expectedToken)
                && leaseExpiresAt != null
                && leaseExpiresAt.isAfter(now);
    }

    private void clearLease() {
        leaseToken = null;
        leaseOwner = null;
        leaseAcquiredAt = null;
        leaseExpiresAt = null;
    }

    private static void requireNow(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("monitor 변경 시각은 필수입니다.");
        }
    }

    private static void requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 값이 올바르지 않습니다.");
        }
    }
}
