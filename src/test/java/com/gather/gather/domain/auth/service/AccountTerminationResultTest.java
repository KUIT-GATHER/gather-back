package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AccountTerminationResultTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 31, 14, 30);

    @Test
    void rejectsNullOutcome() {
        assertThatNullPointerException().isThrownBy(() -> new AccountTerminationResult(null, NOW));
    }

    @Test
    void rejectsNullOccurredAt() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new AccountTerminationResult(
                                        AccountTerminationOutcome.ACCEPTED, null));
    }
}
