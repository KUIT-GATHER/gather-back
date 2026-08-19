package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    // 유니코드 공백은 소스에서 눈으로 구분되지 않으므로 코드포인트로 명시한다.
    private static final String NO_BREAK_SPACE = Character.toString(0x00A0);
    private static final String EM_SPACE = Character.toString(0x2003);
    private static final String IDEOGRAPHIC_SPACE = Character.toString(0x3000);

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"abcdef", "abcdef123456", "password123!"})
    @DisplayName("6자 이상 12자 이하이고 공백이 없으면 통과한다")
    void validate_allowsPolicyCompliantPassword(String password) {
        assertThatCode(() -> passwordPolicy.validate(password, password))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcde", "abcdef1234567"})
    @DisplayName("6자 미만이거나 12자 초과면 VALIDATION_ERROR로 거부한다")
    void validate_rejectsLengthOutOfRange(String password) {
        assertValidationError(password, password);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pass word", "pass\tword", "pass\nword", "pass\rword", "pass\fword"})
    @DisplayName("공백·탭·개행을 포함하면 VALIDATION_ERROR로 거부한다")
    void validate_rejectsAsciiWhitespace(String password) {
        assertValidationError(password, password);
    }

    @Test
    @DisplayName("유니코드 공백을 포함하면 VALIDATION_ERROR로 거부한다")
    void validate_rejectsUnicodeWhitespace() {
        assertValidationError("pass" + NO_BREAK_SPACE + "word", "pass" + NO_BREAK_SPACE + "word");
        assertValidationError("pass" + EM_SPACE + "word", "pass" + EM_SPACE + "word");
        assertValidationError(
                "pass" + IDEOGRAPHIC_SPACE + "word", "pass" + IDEOGRAPHIC_SPACE + "word");
    }

    @Test
    @DisplayName("null과 빈 문자열, 공백뿐인 값은 VALIDATION_ERROR로 거부한다")
    void validate_rejectsNullAndBlank() {
        assertValidationError(null, null);
        assertValidationError("", "");
        assertValidationError("      ", "      ");
    }

    @Test
    @DisplayName("비밀번호 확인이 다르면 PASSWORD_MISMATCH로 거부한다")
    void validate_rejectsConfirmMismatch() {
        assertPasswordMismatch("abcdef", "abcdeg");
        assertPasswordMismatch("abcdef", null);
        assertPasswordMismatch("abcdef", "");
    }

    @Test
    @DisplayName("정책을 위반한 비밀번호는 확인값이 같아도 일치 검사보다 먼저 거부한다")
    void validate_rejectsPolicyViolationBeforeMismatch() {
        assertValidationError("ab c", "ab c");
    }

    private void assertValidationError(String password, String passwordConfirm) {
        assertErrorCode(password, passwordConfirm, ErrorCode.VALIDATION_ERROR);
    }

    private void assertPasswordMismatch(String password, String passwordConfirm) {
        assertErrorCode(password, passwordConfirm, ErrorCode.PASSWORD_MISMATCH);
    }

    private void assertErrorCode(String password, String passwordConfirm, ErrorCode errorCode) {
        assertThatThrownBy(() -> passwordPolicy.validate(password, passwordConfirm))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
