package com.gather.gather.domain.auth.service;

import java.sql.SQLException;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** token hash unique 충돌만 재시도 대상으로 식별한다. 그 밖의 무결성 위반은 원래 실패 흐름을 유지해야 한다. */
@Component
public class PasswordResetTokenConstraintResolver {

    static final String TOKEN_HASH_CONSTRAINT = "uk_password_reset_token_token_hash";
    private static final int MYSQL_DUPLICATE_ENTRY_ERROR_CODE = 1062;

    public boolean isTokenHashConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && containsTokenHashConstraint(constraintViolation.getConstraintName())) {
                return true;
            }
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY_ERROR_CODE
                    && containsTokenHashConstraint(sqlException.getMessage())) {
                return true;
            }
            cause = cause.getCause();
        }
        return containsTokenHashConstraint(exception.getMessage());
    }

    private boolean containsTokenHashConstraint(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(TOKEN_HASH_CONSTRAINT);
    }
}
