package com.gather.gather.global.util;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecognizedMinutesValidatorTest {

    @ParameterizedTest
    @ValueSource(ints = {10, 60, 210})
    @DisplayName("validate accepts positive multiples of 10")
    void validate_accepts_positiveMultipleOfTen(int minutes) {
        assertThatCode(() -> RecognizedMinutesValidator.validate(minutes))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate throws VALIDATION_ERROR when null")
    void validate_throws_whenNull() {
        assertThatThrownBy(() -> RecognizedMinutesValidator.validate(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -10, 15, 25})
    @DisplayName("validate throws VALIDATION_ERROR when not a positive multiple of 10")
    void validate_throws_whenNotPositiveMultipleOfTen(int minutes) {
        assertThatThrownBy(() -> RecognizedMinutesValidator.validate(minutes))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }
}
