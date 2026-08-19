package com.gather.gather.domain.auth.service;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 회원가입과 비밀번호 재설정이 공유하는 비밀번호 정책.
 *
 * <p>정책이 두 곳에서 갈라지면 재설정으로 가입 정책을 우회할 수 있으므로 검증을 한 곳에 둔다.
 */
@Component
public class PasswordPolicy {

    static final int MINIMUM_LENGTH = 6;
    static final int MAXIMUM_LENGTH = 12;

    public void validate(String password, String passwordConfirm) {
        validatePassword(password);
        // 정책을 위반한 값은 확인값과 같더라도 통과시키지 않도록 일치 검사보다 먼저 판정한다.
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
    }

    private void validatePassword(String password) {
        if (password == null
                || password.length() < MINIMUM_LENGTH
                || password.length() > MAXIMUM_LENGTH
                || containsWhitespace(password)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    // ASCII 공백뿐 아니라 NBSP 같은 유니코드 공백까지 막기 위해 두 판정을 함께 사용한다.
    private boolean containsWhitespace(String password) {
        return password.codePoints()
                .anyMatch(
                        codePoint ->
                                Character.isWhitespace(codePoint)
                                        || Character.isSpaceChar(codePoint));
    }
}
