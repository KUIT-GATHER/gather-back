package com.gather.gather.domain.auth.kakao.worker;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoUnlinkRetryPolicy {

    private final KakaoUnlinkWorkerProperties properties;
    private final KakaoUnlinkJitterSource jitterSource;

    public LocalDateTime nextAttemptAt(
            LocalDateTime resultNow, int attemptCount, Instant retryAfterAt) {
        Instant resultInstant = resultNow.toInstant(ZoneOffset.UTC);
        long maximumMillis = properties.maximumBackoff().toMillis();
        long exponentialMillis = exponentialCapMillis(attemptCount, maximumMillis);
        long jitterMillis =
                exponentialMillis == Long.MAX_VALUE
                        ? jitterSource.nextLong(Long.MAX_VALUE)
                        : jitterSource.nextLong(exponentialMillis + 1);
        Instant maximumRetryAt = resultInstant.plusMillis(maximumMillis);
        Instant effectiveRetryAt =
                retryAfterAt == null || !retryAfterAt.isAfter(resultInstant)
                        ? resultInstant
                        : retryAfterAt.isAfter(maximumRetryAt) ? maximumRetryAt : retryAfterAt;
        long retryAfterMillis = Duration.between(resultInstant, effectiveRetryAt).toMillis();
        long delayMillis = Math.max(jitterMillis, retryAfterMillis);
        delayMillis = Math.min(delayMillis, maximumMillis);
        return LocalDateTime.ofInstant(resultInstant.plusMillis(delayMillis), ZoneOffset.UTC);
    }

    private long exponentialCapMillis(int attemptCount, long maximumMillis) {
        long delay = properties.baseBackoff().toMillis();
        for (int index = 1; index < attemptCount && delay < maximumMillis; index++) {
            delay = delay > maximumMillis / 2 ? maximumMillis : delay * 2;
        }
        return Math.min(delay, maximumMillis);
    }
}
