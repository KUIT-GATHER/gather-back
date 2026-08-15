package com.gather.gather.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DuplicateSubmissionGuardTest {

    private final DuplicateSubmissionGuard guard = new DuplicateSubmissionGuard();

    @Test
    void guard_allowsFirstRequest() {
        assertThatCode(() -> guard.guard("key", Duration.ofSeconds(3))).doesNotThrowAnyException();
    }

    @Test
    void guard_blocksSecondRequest_withinCooldown() {
        String key = "post:create:1:100";
        guard.guard(key, Duration.ofSeconds(3));

        assertThatThrownBy(() -> guard.guard(key, Duration.ofSeconds(3)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.DUPLICATE_SUBMISSION));
    }

    @Test
    void guard_allowsAgain_afterCooldownElapses() throws InterruptedException {
        String key = "post:create:2:200";
        guard.guard(key, Duration.ofMillis(50));

        Thread.sleep(80);

        // 쿨다운이 지났으므로 예외 없이 통과해야 한다.
        guard.guard(key, Duration.ofMillis(50));
    }

    @Test
    void guard_treatsDifferentKeys_independently() {
        guard.guard("a", Duration.ofSeconds(3));

        // 다른 key는 쿨다운의 영향을 받지 않는다.
        guard.guard("b", Duration.ofSeconds(3));
    }
}
