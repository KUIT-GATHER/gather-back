package com.gather.gather.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "phone_verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String verificationId;

    @Column(nullable = false, length = 11)
    private String phoneNumber;

    @Column(nullable = false, length = 32)
    private String verificationCode;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime verifiedAt;

    private LocalDateTime consumedAt;

    private LocalDateTime lastConfirmAttemptAt;

    @Column(nullable = false)
    private int confirmAttemptCount;

    private LocalDateTime lastQrRequestedAt;

    @Column(nullable = false)
    private int qrRequestCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private PhoneVerification(
            String verificationId,
            String phoneNumber,
            String verificationCode,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.verificationId = verificationId;
        this.phoneNumber = phoneNumber;
        this.verificationCode = verificationCode;
        this.verified = false;
        this.confirmAttemptCount = 0;
        this.qrRequestCount = 0;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static PhoneVerification create(
            String verificationId,
            String phoneNumber,
            String verificationCode,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        return new PhoneVerification(
                verificationId, phoneNumber, verificationCode, expiresAt, createdAt);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isVerifiedResultExpired(LocalDateTime now, int validityMinutes) {
        return verifiedAt == null || !verifiedAt.plusMinutes(validityMinutes).isAfter(now);
    }

    public void verify(LocalDateTime verifiedAt) {
        if (verified) {
            return;
        }
        this.verified = true;
        this.verifiedAt = verifiedAt;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean canReserveConfirm(LocalDateTime now, Duration cooldown, int maximumAttempts) {
        return confirmAttemptCount < maximumAttempts
                && (lastConfirmAttemptAt == null
                        || !lastConfirmAttemptAt.plus(cooldown).isAfter(now));
    }

    public void reserveConfirm(LocalDateTime now) {
        confirmAttemptCount = Math.addExact(confirmAttemptCount, 1);
        lastConfirmAttemptAt = now;
    }

    public boolean canReserveQr(LocalDateTime now, Duration cooldown, int maximumRequests) {
        return qrRequestCount < maximumRequests
                && (lastQrRequestedAt == null || !lastQrRequestedAt.plus(cooldown).isAfter(now));
    }

    public void reserveQr(LocalDateTime now) {
        qrRequestCount = Math.addExact(qrRequestCount, 1);
        lastQrRequestedAt = now;
    }

    public void consume(LocalDateTime consumedAt) {
        if (isConsumed()) {
            return;
        }
        this.consumedAt = consumedAt;
    }
}
