package com.gather.gather.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "kakao_unlink_task",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_kakao_unlink_task_social_account_generation",
                        columnNames = {"social_account_id", "generation"}),
        indexes = {
            @Index(name = "idx_kakao_unlink_task_due", columnList = "status, next_attempt_at, id"),
            @Index(
                    name = "idx_kakao_unlink_task_lease_recovery",
                    columnList = "status, lease_expires_at, id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoUnlinkTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "social_account_id", nullable = false)
    private SocialAccount socialAccount;

    @Column(nullable = false)
    private long generation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KakaoUnlinkTaskStatus status;

    @Column(nullable = false)
    private int retryCycle;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column private LocalDateTime lastAttemptAt;

    @Column(length = 64)
    private String claimToken;

    @Column(length = 128)
    private String claimedBy;

    @Column private LocalDateTime claimedAt;

    @Column private LocalDateTime leaseExpiresAt;

    @Column private Integer lastHttpStatus;

    @Column private Integer lastKakaoCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private KakaoUnlinkTaskErrorType lastErrorType;

    @Column private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    private KakaoUnlinkTask(SocialAccount socialAccount, long generation, LocalDateTime createdAt) {
        if (socialAccount == null) {
            throw new IllegalArgumentException("카카오 연결 해제 대상 소셜 계정은 필수입니다.");
        }
        if (generation <= 0) {
            throw new IllegalArgumentException("카카오 연결 해제 대상 세대는 1 이상이어야 합니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("카카오 연결 해제 작업 생성 시각은 필수입니다.");
        }

        this.socialAccount = socialAccount;
        this.generation = generation;
        this.status = KakaoUnlinkTaskStatus.PENDING;
        this.retryCycle = 0;
        this.attemptCount = 0;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static KakaoUnlinkTask pending(
            SocialAccount socialAccount, long generation, LocalDateTime now) {
        return new KakaoUnlinkTask(socialAccount, generation, now);
    }

    public void claim(
            String newClaimToken,
            String workerIdentifier,
            LocalDateTime claimedAt,
            LocalDateTime leaseExpiresAt) {
        if (status != KakaoUnlinkTaskStatus.PENDING) {
            throw new IllegalStateException("PENDING task만 claim할 수 있습니다.");
        }
        applyClaim(newClaimToken, workerIdentifier, claimedAt, leaseExpiresAt);
    }

    public void reclaim(
            String newClaimToken,
            String workerIdentifier,
            LocalDateTime claimedAt,
            LocalDateTime leaseExpiresAt) {
        if (status != KakaoUnlinkTaskStatus.PROCESSING
                || this.leaseExpiresAt == null
                || this.leaseExpiresAt.isAfter(claimedAt)) {
            throw new IllegalStateException("lease가 만료된 PROCESSING task만 reclaim할 수 있습니다.");
        }
        applyClaim(newClaimToken, workerIdentifier, claimedAt, leaseExpiresAt);
    }

    public int reserveAttempt(
            String expectedClaimToken,
            LocalDateTime leaseNow,
            LocalDateTime attemptNow,
            int maximumAttempts) {
        requireOwnedClaim(expectedClaimToken, leaseNow);
        requireNow(attemptNow, "attempt reservation 시각");
        if (maximumAttempts <= 0 || attemptCount >= maximumAttempts) {
            throw new IllegalStateException("attempt reservation 예산이 소진되었습니다.");
        }
        attemptCount = Math.addExact(attemptCount, 1);
        lastAttemptAt = attemptNow;
        updatedAt = attemptNow;
        return attemptCount;
    }

    public void scheduleRetry(
            String expectedClaimToken,
            LocalDateTime leaseNow,
            LocalDateTime nextAttemptAt,
            LocalDateTime resultNow,
            Integer httpStatus,
            Integer kakaoCode) {
        requireOwnedClaim(expectedClaimToken, leaseNow);
        requireNow(nextAttemptAt, "다음 attempt 시각");
        requireNow(resultNow, "retry 결과 시각");
        if (nextAttemptAt.isBefore(resultNow)) {
            throw new IllegalArgumentException("다음 attempt 시각은 결과 시각보다 빠를 수 없습니다.");
        }
        status = KakaoUnlinkTaskStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        recordDiagnostic(httpStatus, kakaoCode, KakaoUnlinkTaskErrorType.RETRYABLE);
        clearClaim();
        updatedAt = resultNow;
    }

    public void succeed(
            String expectedClaimToken, LocalDateTime leaseNow, LocalDateTime completedAt) {
        requireOwnedClaim(expectedClaimToken, leaseNow);
        transitionTerminal(KakaoUnlinkTaskStatus.SUCCEEDED, completedAt, null, null, null);
    }

    public void dead(
            String expectedClaimToken,
            LocalDateTime leaseNow,
            LocalDateTime completedAt,
            Integer httpStatus,
            Integer kakaoCode,
            KakaoUnlinkTaskErrorType errorType) {
        if (errorType == null || !errorType.isDeadCompatible()) {
            throw new IllegalArgumentException("DEAD와 결합할 수 있는 terminal 오류 분류가 필요합니다.");
        }
        requireOwnedClaim(expectedClaimToken, leaseNow);
        transitionTerminal(
                KakaoUnlinkTaskStatus.DEAD, completedAt, httpStatus, kakaoCode, errorType);
    }

    public void stale(
            String expectedClaimToken, LocalDateTime leaseNow, LocalDateTime completedAt) {
        requireOwnedClaim(expectedClaimToken, leaseNow);
        transitionTerminal(
                KakaoUnlinkTaskStatus.STALE,
                completedAt,
                null,
                null,
                KakaoUnlinkTaskErrorType.STALE);
    }

    public void startNewRetryCycle(LocalDateTime resumeNow) {
        requireNow(resumeNow, "retry cycle 재개 시각");
        if (status != KakaoUnlinkTaskStatus.DEAD
                || lastErrorType != KakaoUnlinkTaskErrorType.CONFIGURATION) {
            throw new IllegalStateException("configuration 원인의 DEAD task만 재개할 수 있습니다.");
        }
        retryCycle = Math.addExact(retryCycle, 1);
        attemptCount = 0;
        status = KakaoUnlinkTaskStatus.PENDING;
        nextAttemptAt = resumeNow;
        lastAttemptAt = null;
        completedAt = null;
        recordDiagnostic(null, null, null);
        clearClaim();
        updatedAt = resumeNow;
    }

    public boolean hasOwnedValidClaim(String expectedClaimToken, LocalDateTime leaseNow) {
        return status == KakaoUnlinkTaskStatus.PROCESSING
                && claimToken != null
                && claimToken.equals(expectedClaimToken)
                && leaseExpiresAt != null
                && leaseNow != null
                && leaseExpiresAt.isAfter(leaseNow);
    }

    private void applyClaim(
            String newClaimToken,
            String workerIdentifier,
            LocalDateTime claimedAt,
            LocalDateTime leaseExpiresAt) {
        if (newClaimToken == null || newClaimToken.isBlank()) {
            throw new IllegalArgumentException("claim token은 필수입니다.");
        }
        if (workerIdentifier == null || workerIdentifier.isBlank()) {
            throw new IllegalArgumentException("worker 식별자는 필수입니다.");
        }
        requireNow(claimedAt, "claim 시각");
        requireNow(leaseExpiresAt, "lease 만료 시각");
        if (!leaseExpiresAt.isAfter(claimedAt)) {
            throw new IllegalArgumentException("lease 만료 시각은 claim 시각보다 늦어야 합니다.");
        }
        status = KakaoUnlinkTaskStatus.PROCESSING;
        claimToken = newClaimToken;
        claimedBy = workerIdentifier;
        this.claimedAt = claimedAt;
        this.leaseExpiresAt = leaseExpiresAt;
        updatedAt = claimedAt;
    }

    private void requireOwnedClaim(String expectedClaimToken, LocalDateTime leaseNow) {
        if (!hasOwnedValidClaim(expectedClaimToken, leaseNow)) {
            throw new IllegalStateException("유효한 claim 소유권이 없습니다.");
        }
    }

    private void transitionTerminal(
            KakaoUnlinkTaskStatus terminalStatus,
            LocalDateTime completedAt,
            Integer httpStatus,
            Integer kakaoCode,
            KakaoUnlinkTaskErrorType errorType) {
        requireNow(completedAt, "terminal 완료 시각");
        status = terminalStatus;
        this.completedAt = completedAt;
        recordDiagnostic(httpStatus, kakaoCode, errorType);
        clearClaim();
        updatedAt = completedAt;
    }

    private void recordDiagnostic(
            Integer httpStatus, Integer kakaoCode, KakaoUnlinkTaskErrorType errorType) {
        lastHttpStatus = httpStatus;
        lastKakaoCode = kakaoCode;
        lastErrorType = errorType;
    }

    private void clearClaim() {
        claimToken = null;
        claimedBy = null;
        claimedAt = null;
        leaseExpiresAt = null;
    }

    private static void requireNow(LocalDateTime now, String fieldName) {
        if (now == null) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
    }
}
