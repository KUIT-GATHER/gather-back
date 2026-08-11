package com.gather.gather.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "email_verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 36)
    private String verificationId;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime verifiedAt;

    private LocalDateTime consumedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private int dailySendCount;

    @Column(nullable = false)
    private int attemptCount;

    private EmailVerification(
            String email,
            String verificationId,
            String code,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.email = email;
        this.verificationId = verificationId;
        this.code = code;
        this.verified = false;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.dailySendCount = 1;
    }

    public static EmailVerification create(
            String email,
            String verificationId,
            String code,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        return new EmailVerification(email, verificationId, code, expiresAt, createdAt);
    }

    public void refresh(
            String verificationId,
            String code,
            LocalDateTime expiresAt,
            LocalDateTime refreshedAt) {
        // createdAt(직전 발송 시각)을 갱신하기 전에 당일 발송 횟수를 먼저 계산해야 한다.
        this.dailySendCount = dailySendCountAsOf(refreshedAt.toLocalDate()) + 1;
        this.verificationId = verificationId;
        this.code = code;
        this.expiresAt = expiresAt;
        this.verified = false;
        this.verifiedAt = null;
        this.consumedAt = null;
        this.attemptCount = 0;
        this.createdAt = refreshedAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isWithinResendCooldown(LocalDateTime now, int cooldownMinutes) {
        return createdAt.plusMinutes(cooldownMinutes).isAfter(now);
    }

    public int dailySendCountAsOf(LocalDate date) {
        return createdAt.toLocalDate().isEqual(date) ? dailySendCount : 0;
    }

    public boolean isAttemptExceeded(int maxAttempts) {
        return attemptCount >= maxAttempts;
    }

    public void increaseAttempt() {
        this.attemptCount++;
    }

    public void verify(LocalDateTime verifiedAt) {
        this.verified = true;
        this.verifiedAt = verifiedAt;
    }

    public boolean isVerifiedResultExpired(LocalDateTime now, int validityMinutes) {
        return verifiedAt == null || !verifiedAt.plusMinutes(validityMinutes).isAfter(now);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public void consume(LocalDateTime consumedAt) {
        if (isConsumed()) {
            return;
        }
        this.consumedAt = consumedAt;
    }
}
