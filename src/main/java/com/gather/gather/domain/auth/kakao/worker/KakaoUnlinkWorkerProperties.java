package com.gather.gather.domain.auth.kakao.worker;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "kakao.admin.unlink-worker")
public record KakaoUnlinkWorkerProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("30s") Duration pollInterval,
        @DefaultValue("10") int batchSize,
        @DefaultValue("120s") Duration leaseDuration,
        @DefaultValue("12") int maximumAttempts,
        @DefaultValue("1m") Duration baseBackoff,
        @DefaultValue("6h") Duration maximumBackoff,
        @DefaultValue("local") String workerIdentifier) {

    private static final Duration HARD_MAXIMUM_BACKOFF = Duration.ofHours(6);

    public KakaoUnlinkWorkerProperties {
        requirePositive(pollInterval, "poll-interval");
        requirePositive(leaseDuration, "lease-duration");
        requirePositive(baseBackoff, "base-backoff");
        requirePositive(maximumBackoff, "maximum-backoff");
        if (batchSize <= 0) {
            throw new IllegalStateException("kakao.admin.unlink-worker.batch-size는 0보다 커야 합니다.");
        }
        if (maximumAttempts <= 0 || maximumAttempts > 12) {
            throw new IllegalStateException(
                    "kakao.admin.unlink-worker.maximum-attempts는 1 이상 12 이하여야 합니다.");
        }
        if (baseBackoff.compareTo(maximumBackoff) > 0) {
            throw new IllegalStateException("base-backoff는 maximum-backoff보다 클 수 없습니다.");
        }
        if (maximumBackoff.compareTo(HARD_MAXIMUM_BACKOFF) > 0) {
            throw new IllegalStateException("maximum-backoff must not exceed six hours");
        }
        if (workerIdentifier == null
                || workerIdentifier.isBlank()
                || workerIdentifier.length() > 128) {
            throw new IllegalStateException("worker-identifier는 1~128자여야 합니다.");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("kakao.admin.unlink-worker." + name + "은 0보다 커야 합니다.");
        }
    }
}
