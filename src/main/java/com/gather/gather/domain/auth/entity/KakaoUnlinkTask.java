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

    @Column(length = 40)
    private String lastErrorType;

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
        this.attemptCount = 0;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static KakaoUnlinkTask pending(
            SocialAccount socialAccount, long generation, LocalDateTime now) {
        return new KakaoUnlinkTask(socialAccount, generation, now);
    }
}
