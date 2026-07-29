package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @MethodSource("invalidRequiredValues")
    void create_rejectsMissingRequiredValue(
            AccountRejoinBlockIdentifierType identifierType,
            String identifierHash,
            LocalDateTime expiresAt,
            Long sourceUserId,
            LocalDateTime createdAt) {
        assertThatThrownBy(
                        () ->
                                AccountRejoinBlock.create(
                                        identifierType,
                                        identifierHash,
                                        1,
                                        expiresAt,
                                        sourceUserId,
                                        createdAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재가입 제한 필수 값이 누락되었습니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void create_rejectsNonPositiveKeyVersion(int keyVersion) {
        assertThatThrownBy(
                        () ->
                                AccountRejoinBlock.create(
                                        AccountRejoinBlockIdentifierType.PHONE,
                                        "a".repeat(64),
                                        keyVersion,
                                        EXPIRES_AT,
                                        1L,
                                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재가입 제한 키 버전은 1 이상이어야 합니다.");
    }

    private static Stream<Arguments> invalidRequiredValues() {
        return Stream.of(
                Arguments.of(null, "a".repeat(64), EXPIRES_AT, 1L, CREATED_AT),
                Arguments.of(
                        AccountRejoinBlockIdentifierType.PHONE, null, EXPIRES_AT, 1L, CREATED_AT),
                Arguments.of(
                        AccountRejoinBlockIdentifierType.PHONE, " ", EXPIRES_AT, 1L, CREATED_AT),
                Arguments.of(
                        AccountRejoinBlockIdentifierType.PHONE,
                        "a".repeat(64),
                        null,
                        1L,
                        CREATED_AT),
                Arguments.of(
                        AccountRejoinBlockIdentifierType.PHONE,
                        "a".repeat(64),
                        EXPIRES_AT,
                        null,
                        CREATED_AT),
                Arguments.of(
                        AccountRejoinBlockIdentifierType.PHONE,
                        "a".repeat(64),
                        EXPIRES_AT,
                        1L,
                        null));
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
