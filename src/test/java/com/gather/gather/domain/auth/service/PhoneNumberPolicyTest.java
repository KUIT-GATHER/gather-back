package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneNumberPolicyTest {

    private final PhoneNumberPolicy policy = new PhoneNumberPolicy();

    @Test
    @DisplayName("숫자, 공백, 하이픈 표기를 하나의 국내 휴대폰 번호로 정규화한다")
    void normalize_acceptsSupportedFormatting() {
        assertThat(policy.normalize("01012345678")).isEqualTo("01012345678");
        assertThat(policy.normalize("010-1234-5678")).isEqualTo("01012345678");
        assertThat(policy.normalize(" 010 1234 5678 ")).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("국가번호와 유선 번호는 자동 변환하지 않고 거부한다")
    void normalize_rejectsUnsupportedNumbers() {
        assertValidationError(() -> policy.normalize("+821012345678"));
        assertValidationError(() -> policy.normalize("0212345678"));
    }

    @Test
    @DisplayName("구분자 제거 후 숫자가 아닌 문자가 있으면 거부한다")
    void normalize_rejectsNonNumericCharacters() {
        assertValidationError(() -> policy.normalize("010-1234-abcd"));
    }

    private void assertValidationError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
