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

    // 구 버전 JAR로 롤백돼도 스키마가 맞도록 code 컬럼을 남겨두고, 새 애플리케이션은 여기에 빈 문자열만 넣는다.
    // 구 버전은 저장된 code와 입력값을 문자열로 비교하므로, 빈 문자열이면 @NotBlank를 통과한 어떤 입력도 인증되지 않는다.
    private static final String COMPATIBILITY_CODE = "";

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

    @Column(length = 64)
    private String codeHash;

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
            String codeHash,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.email = email;
        this.verificationId = verificationId;
        this.code = COMPATIBILITY_CODE;
        this.codeHash = codeHash;
        this.verified = false;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.dailySendCount = 1;
    }

    public static EmailVerification create(
            String email,
            String verificationId,
            String codeHash,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        return new EmailVerification(email, verificationId, codeHash, expiresAt, createdAt);
    }

    public void refresh(
            String verificationId,
            String codeHash,
            LocalDateTime expiresAt,
            LocalDateTime refreshedAt) {
        // createdAt(직전 발송 시각)을 갱신하기 전에 당일 발송 횟수를 먼저 계산해야 한다.
        this.dailySendCount = dailySendCountAsOf(refreshedAt.toLocalDate()) + 1;
        this.verificationId = verificationId;
        this.code = COMPATIBILITY_CODE;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.verified = false;
        this.verifiedAt = null;
        this.consumedAt = null;
        this.attemptCount = 0;
        this.createdAt = refreshedAt;
    }

    /**
     * 구 버전 JAR이 만들었거나 갱신한 행인지 판정한다.
     *
     * <p>구 버전은 code에 평문을 넣고 code_hash는 그대로 두므로, 이 상태의 행은 현재 검증 방식으로 신뢰할 수 없어 인증에 사용하지 않는다.
     */
    public boolean isLegacyFormat() {
        return !COMPATIBILITY_CODE.equals(code) || codeHash == null;
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
