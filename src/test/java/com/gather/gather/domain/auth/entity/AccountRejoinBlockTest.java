package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AccountRejoinBlockTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 29, 12, 0);
    private static final LocalDateTime EXPIRES_AT = CREATED_AT.plusDays(7);

    @Test
    void isActiveAt_returnsTrueBeforeExpiration() {
        AccountRejoinBlock block = block();

        assertThat(block.isActiveAt(EXPIRES_AT.minusNanos(1))).isTrue();
    }

    @Test
    void isActiveAt_returnsFalseAtExactExpiration() {
        AccountRejoinBlock block = block();

        assertThat(block.isActiveAt(EXPIRES_AT)).isFalse();
    }

    @Test
    void extendUntil_extendsOnlyToLaterExpiration() {
        AccountRejoinBlock block = block();
        LocalDateTime laterExpiration = EXPIRES_AT.plusDays(1);

        block.extendUntil(laterExpiration);

        assertThat(block.getExpiresAt()).isEqualTo(laterExpiration);
    }

    @Test
    void extendUntil_doesNotShortenExpiration() {
        AccountRejoinBlock block = block();

        block.extendUntil(EXPIRES_AT.minusDays(1));

        assertThat(block.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    private AccountRejoinBlock block() {
        return AccountRejoinBlock.create(
                AccountRejoinBlockIdentifierType.PHONE,
                "a".repeat(64),
                1,
                EXPIRES_AT,
                1L,
                CREATED_AT);
    }
}
