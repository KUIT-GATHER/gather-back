package com.gather.gather.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "gather.auth.phone-verification")
public record PhoneVerificationProperties(
        @DefaultValue("60s") Duration startCooldown,
        @DefaultValue("3s") Duration confirmCooldown,
        @DefaultValue("30") int confirmMaxAttempts,
        @DefaultValue("10s") Duration qrCooldown,
        @DefaultValue("3") int qrMaxAttempts) {

    public PhoneVerificationProperties {
        requirePositive(startCooldown, "start-cooldown");
        requirePositive(confirmCooldown, "confirm-cooldown");
        requirePositive(qrCooldown, "qr-cooldown");
        if (confirmMaxAttempts <= 0) {
            throw new IllegalStateException("confirm-max-attempts는 양수여야 합니다.");
        }
        if (qrMaxAttempts <= 0) {
            throw new IllegalStateException("qr-max-attempts는 양수여야 합니다.");
        }
    }

    private static void requirePositive(Duration duration, String propertyName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(propertyName + "은 양수여야 합니다.");
        }
    }
}
