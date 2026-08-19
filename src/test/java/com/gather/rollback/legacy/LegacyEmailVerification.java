package com.gather.rollback.legacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * V65 이전 JAR이 쓰던 email_verification 매핑 사본.
 *
 * <p>롤백된 구 버전이 확장된 스키마에서 그대로 기동·저장할 수 있는지 검증하는 데만 쓴다. 애플리케이션 엔티티 스캔 대상인 {@code com.gather.gather}
 * 밖에 두어 운영 컨텍스트에 섞이지 않게 한다. 물리 컬럼명을 명시해 네이밍 전략 설정과 무관하게 같은 매핑이 되도록 한다.
 */
@Entity(name = "LegacyEmailVerification")
@Table(name = "email_verification")
public class LegacyEmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "verification_id", nullable = false, unique = true, length = 36)
    private String verificationId;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "daily_send_count", nullable = false)
    private int dailySendCount;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected LegacyEmailVerification() {}

    public LegacyEmailVerification(
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

    public String getCode() {
        return code;
    }

    public String getVerificationId() {
        return verificationId;
    }

    /** 구 버전의 인증 코드 비교 방식. */
    public boolean matchesCode(String inputCode) {
        return code.equals(inputCode);
    }

    public void refresh(String verificationId, String code, LocalDateTime refreshedAt) {
        this.verificationId = verificationId;
        this.code = code;
        this.dailySendCount = this.dailySendCount + 1;
        this.createdAt = refreshedAt;
    }
}
