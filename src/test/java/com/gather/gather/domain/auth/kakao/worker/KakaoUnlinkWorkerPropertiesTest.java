package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KakaoUnlinkWorkerPropertiesTest {

    private static final Duration SECOND = Duration.ofSeconds(1);

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProperties")
    void rejectsEveryInvalidConfigurationBranch(
            String name, PropertiesInput input, String message) {
        assertThatThrownBy(
                        () ->
                                new KakaoUnlinkWorkerProperties(
                                        false,
                                        input.pollInterval(),
                                        input.batchSize(),
                                        input.leaseDuration(),
                                        input.maximumAttempts(),
                                        input.baseBackoff(),
                                        input.maximumBackoff(),
                                        input.workerIdentifier()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    @Test
    void acceptsMinimumAndMaximumInclusiveBoundaries() {
        assertThatCode(
                        () ->
                                properties(
                                        Duration.ofNanos(1),
                                        1,
                                        Duration.ofNanos(1),
                                        1,
                                        Duration.ofNanos(1),
                                        Duration.ofNanos(1),
                                        "a"))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                properties(
                                        SECOND,
                                        1,
                                        SECOND,
                                        12,
                                        SECOND,
                                        Duration.ofHours(6),
                                        "a".repeat(128)))
                .doesNotThrowAnyException();
    }

    static Stream<Arguments> invalidProperties() {
        return Stream.of(
                invalid(
                        "null poll interval",
                        null,
                        10,
                        SECOND,
                        12,
                        SECOND,
                        SECOND,
                        "worker",
                        "poll-interval"),
                invalid(
                        "zero poll interval",
                        Duration.ZERO,
                        10,
                        SECOND,
                        12,
                        SECOND,
                        SECOND,
                        "worker",
                        "poll-interval"),
                invalid(
                        "negative lease duration",
                        SECOND,
                        10,
                        Duration.ofSeconds(-1),
                        12,
                        SECOND,
                        SECOND,
                        "worker",
                        "lease-duration"),
                invalid(
                        "zero base backoff",
                        SECOND,
                        10,
                        SECOND,
                        12,
                        Duration.ZERO,
                        SECOND,
                        "worker",
                        "base-backoff"),
                invalid(
                        "zero maximum backoff",
                        SECOND,
                        10,
                        SECOND,
                        12,
                        SECOND,
                        Duration.ZERO,
                        "worker",
                        "maximum-backoff"),
                invalid(
                        "non-positive batch size",
                        SECOND,
                        0,
                        SECOND,
                        12,
                        SECOND,
                        SECOND,
                        "worker",
                        "batch-size"),
                invalid(
                        "attempts below minimum",
                        SECOND,
                        10,
                        SECOND,
                        0,
                        SECOND,
                        SECOND,
                        "worker",
                        "maximum-attempts"),
                invalid(
                        "attempts above maximum",
                        SECOND,
                        10,
                        SECOND,
                        13,
                        SECOND,
                        SECOND,
                        "worker",
                        "maximum-attempts"),
                invalid(
                        "base exceeds maximum",
                        SECOND,
                        10,
                        SECOND,
                        12,
                        Duration.ofSeconds(2),
                        SECOND,
                        "worker",
                        "base-backoff"),
                invalid(
                        "maximum exceeds hard cap",
                        SECOND,
                        10,
                        SECOND,
                        12,
                        SECOND,
                        Duration.ofHours(6).plusNanos(1),
                        "worker",
                        "six hours"),
                invalid(
                        "null worker identifier",
                        SECOND,
                        10,
                        SECOND,
                        12,
                        SECOND,
                        SECOND,
                        null,
                        "worker-identifier"),
                invalid(
                        "blank worker identifier",
                        SECOND,
                        10,
                        SECOND,
                        12,
                        SECOND,
                        SECOND,
                        " ",
                        "worker-identifier"),
                invalid(
                        "worker identifier too long",
                        SECOND,
                        10,
                        SECOND,
                        12,
                        SECOND,
                        SECOND,
                        "a".repeat(129),
                        "worker-identifier"));
    }

    private static Arguments invalid(
            String name,
            Duration pollInterval,
            int batchSize,
            Duration leaseDuration,
            int maximumAttempts,
            Duration baseBackoff,
            Duration maximumBackoff,
            String workerIdentifier,
            String message) {
        return Arguments.of(
                name,
                new PropertiesInput(
                        pollInterval,
                        batchSize,
                        leaseDuration,
                        maximumAttempts,
                        baseBackoff,
                        maximumBackoff,
                        workerIdentifier),
                message);
    }

    private static KakaoUnlinkWorkerProperties properties(
            Duration pollInterval,
            int batchSize,
            Duration leaseDuration,
            int maximumAttempts,
            Duration baseBackoff,
            Duration maximumBackoff,
            String workerIdentifier) {
        return new KakaoUnlinkWorkerProperties(
                false,
                pollInterval,
                batchSize,
                leaseDuration,
                maximumAttempts,
                baseBackoff,
                maximumBackoff,
                workerIdentifier);
    }

    private record PropertiesInput(
            Duration pollInterval,
            int batchSize,
            Duration leaseDuration,
            int maximumAttempts,
            Duration baseBackoff,
            Duration maximumBackoff,
            String workerIdentifier) {}
}
