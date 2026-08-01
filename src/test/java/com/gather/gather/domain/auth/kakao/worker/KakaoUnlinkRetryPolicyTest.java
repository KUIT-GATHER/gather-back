package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class KakaoUnlinkRetryPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final KakaoUnlinkWorkerProperties PROPERTIES =
            new KakaoUnlinkWorkerProperties(
                    true,
                    Duration.ofSeconds(30),
                    10,
                    Duration.ofMinutes(2),
                    12,
                    Duration.ofMinutes(1),
                    Duration.ofHours(6),
                    "test-worker");

    @Test
    void nextAttemptAt_usesFullJitterForExponentialBackoff() {
        KakaoUnlinkRetryPolicy policy = new KakaoUnlinkRetryPolicy(PROPERTIES, bound -> bound - 1);

        LocalDateTime nextAttemptAt = policy.nextAttemptAt(NOW, 3, null);

        assertThat(nextAttemptAt).isEqualTo(NOW.plusMinutes(4));
    }

    @Test
    void nextAttemptAt_usesLaterRetryAfter() {
        KakaoUnlinkRetryPolicy policy = new KakaoUnlinkRetryPolicy(PROPERTIES, bound -> 0);
        Instant retryAfterAt = NOW.toInstant(ZoneOffset.UTC).plus(Duration.ofHours(2));

        assertThat(policy.nextAttemptAt(NOW, 1, retryAfterAt)).isEqualTo(NOW.plusHours(2));
    }

    @Test
    void nextAttemptAt_capsRetryAfterAndExponentialBackoffAtSixHours() {
        KakaoUnlinkRetryPolicy policy = new KakaoUnlinkRetryPolicy(PROPERTIES, bound -> bound - 1);

        LocalDateTime nextAttemptAt = policy.nextAttemptAt(NOW, 12, Instant.MAX);

        assertThat(nextAttemptAt).isEqualTo(NOW.plusHours(6));
    }

    @Test
    void nextAttemptAt_ignoresPastRetryAfter() {
        KakaoUnlinkRetryPolicy policy = new KakaoUnlinkRetryPolicy(PROPERTIES, bound -> 0);

        assertThat(policy.nextAttemptAt(NOW, 1, NOW.toInstant(ZoneOffset.UTC).minusSeconds(1)))
                .isEqualTo(NOW);
    }

    @Test
    void properties_rejectMaximumBackoffOverSixHours() {
        assertThatThrownBy(
                        () ->
                                new KakaoUnlinkWorkerProperties(
                                        true,
                                        Duration.ofSeconds(30),
                                        10,
                                        Duration.ofMinutes(2),
                                        12,
                                        Duration.ofMinutes(1),
                                        Duration.ofHours(7),
                                        "test-worker"))
                .isInstanceOf(IllegalStateException.class);
    }
}
