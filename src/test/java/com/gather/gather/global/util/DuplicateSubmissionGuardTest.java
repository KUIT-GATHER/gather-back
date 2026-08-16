package com.gather.gather.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DuplicateSubmissionGuardTest {

    private static final Instant BASE = Instant.parse("2026-08-16T00:00:00Z");

    private final MutableClock clock = new MutableClock(BASE);
    private final DuplicateSubmissionGuard guard = new DuplicateSubmissionGuard(clock);

    @Test
    void guard_allowsFirstRequest() {
        assertThatCode(() -> guard.guard("key", Duration.ofSeconds(3))).doesNotThrowAnyException();
    }

    @Test
    void guard_blocksSecondRequest_withinCooldown() {
        String key = "post:create:1:100";
        guard.guard(key, Duration.ofSeconds(3));
        clock.advance(Duration.ofSeconds(1));

        assertThatThrownBy(() -> guard.guard(key, Duration.ofSeconds(3)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_SUBMISSION));
    }

    /**
     * B1 회귀 테스트. 리뷰에서 지적된 버그: 두 호출의 {@code Instant.now()}가 완전히 같은 값을 반환하면(해상도가 낮은 Clock 등)
     * 기존 구현은 {@code Instant.equals()}로 통과 여부를 판정해 두 번째 요청도 통과시켰다. 통과 여부를 별도 플래그로 판정하도록 고친
     * 뒤에는, 같은 Instant를 받더라도 두 번째 호출은 반드시 막혀야 한다.
     */
    @Test
    void guard_blocksSecondRequest_whenBothCallsReceiveTheExactSameInstant() {
        String key = "post:create:1:100";
        clock.freezeAt(BASE);

        guard.guard(key, Duration.ofSeconds(3));

        assertThatThrownBy(() -> guard.guard(key, Duration.ofSeconds(3)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_SUBMISSION));
    }

    @Test
    void guard_allowsAgain_afterCooldownElapses() {
        String key = "post:create:2:200";
        guard.guard(key, Duration.ofSeconds(3));
        clock.advance(Duration.ofSeconds(3).plusMillis(1));

        assertThatCode(() -> guard.guard(key, Duration.ofSeconds(3))).doesNotThrowAnyException();
    }

    @Test
    void guard_allowsAgain_atExactCooldownBoundary() {
        // last.plus(cooldown).isAfter(now)이므로 now == last+cooldown인 경계 시각은 "지남"으로 취급되어 허용된다.
        String key = "post:create:3:300";
        guard.guard(key, Duration.ofSeconds(3));
        clock.advance(Duration.ofSeconds(3));

        assertThatCode(() -> guard.guard(key, Duration.ofSeconds(3))).doesNotThrowAnyException();
    }

    @Test
    void guard_treatsDifferentKeys_independently() {
        guard.guard("a", Duration.ofSeconds(3));

        assertThatCode(() -> guard.guard("b", Duration.ofSeconds(3))).doesNotThrowAnyException();
    }

    @Test
    void guard_defaultOverload_blocksImmediateRetry() {
        // 서비스 호출부는 전부 기본(3초) 오버로드만 사용하므로, DEFAULT_COOLDOWN 값 자체가 회귀되지 않는지 확인한다.
        String key = "post:create:4:400";
        guard.guard(key);

        assertThatThrownBy(() -> guard.guard(key))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_SUBMISSION));
    }

    @Test
    void guard_admitsExactlyOneRequest_whenCalledConcurrentlyWithSameKey()
            throws InterruptedException {
        String key = "post:create:5:500";
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admittedCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        ready.countDown();
                        try {
                            start.await();
                            guard.guard(key, Duration.ofSeconds(3));
                            admittedCount.incrementAndGet();
                        } catch (BusinessException ignored) {
                            // 중복으로 거부된 요청 — 정상 동작.
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(admittedCount.get()).isEqualTo(1);
    }

    /** 테스트에서 시각을 직접 제어하기 위한 단순 가변 {@link Clock}. */
    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        void freezeAt(Instant fixed) {
            this.instant = fixed;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
