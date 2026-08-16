package com.gather.gather.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "kakao_unlink_worker_control")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoUnlinkWorkerControl {

    public static final long SINGLETON_ID = 1L;

    @Id private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KakaoUnlinkWorkerControlStatus status;

    @Column private LocalDateTime blockedAt;

    @Column(length = 40)
    private String blockedReason;

    @Column private Integer lastHttpStatus;

    @Column private Integer lastKakaoCode;

    @Column private LocalDateTime lastPollStartedAt;

    @Column private LocalDateTime lastPollCompletedAt;

    @Column private LocalDateTime lastPollFailedAt;

    @Column(length = 80)
    private String lastPollFailureType;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public boolean isActive() {
        return status == KakaoUnlinkWorkerControlStatus.ACTIVE;
    }

    public boolean isConfigurationBlocked() {
        return status == KakaoUnlinkWorkerControlStatus.CONFIGURATION_BLOCKED;
    }

    public void blockConfiguration(LocalDateTime now, Integer httpStatus, Integer kakaoCode) {
        requireNow(now);
        this.status = KakaoUnlinkWorkerControlStatus.CONFIGURATION_BLOCKED;
        this.blockedAt = now;
        this.blockedReason = KakaoUnlinkTaskErrorType.CONFIGURATION.name();
        this.lastHttpStatus = httpStatus;
        this.lastKakaoCode = kakaoCode;
        this.updatedAt = now;
    }

    public void resume(LocalDateTime now) {
        requireNow(now);
        if (status != KakaoUnlinkWorkerControlStatus.CONFIGURATION_BLOCKED) {
            throw new IllegalStateException("설정 오류로 차단된 worker만 재개할 수 있습니다.");
        }
        this.status = KakaoUnlinkWorkerControlStatus.ACTIVE;
        this.blockedAt = null;
        this.blockedReason = null;
        this.lastHttpStatus = null;
        this.lastKakaoCode = null;
        this.updatedAt = now;
    }

    private static void requireNow(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("worker control 변경 시각은 필수입니다.");
        }
    }
}
