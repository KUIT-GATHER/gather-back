package com.gather.gather.domain.auth.service;

import java.sql.SQLException;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class SocialAccountConstraintResolver {

    static final String PROVIDER_KEY_CONSTRAINT = "uk_social_account_provider_key";
    static final String LEGACY_PROVIDER_USER_ID_CONSTRAINT = "uk_social_account_provider_user";
    static final String USER_PROVIDER_CONSTRAINT = "uk_social_account_user_provider";
    private static final int MYSQL_DUPLICATE_ENTRY_ERROR_CODE = 1062;

    public SocialAccountConstraint resolve(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                SocialAccountConstraint resolved =
                        resolveConstraintName(constraintViolation.getConstraintName());
                if (resolved != SocialAccountConstraint.UNKNOWN) {
                    return resolved;
                }
            }
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY_ERROR_CODE) {
                SocialAccountConstraint resolved = resolveConstraintName(sqlException.getMessage());
                if (resolved != SocialAccountConstraint.UNKNOWN) {
                    return resolved;
                }
            }
            cause = cause.getCause();
        }
        return resolveConstraintName(exception.getMessage());
    }

    private SocialAccountConstraint resolveConstraintName(String value) {
        if (value == null) {
            return SocialAccountConstraint.UNKNOWN;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.contains(PROVIDER_KEY_CONSTRAINT)) {
            return SocialAccountConstraint.PROVIDER_USER_KEY;
        }
        if (normalized.contains(LEGACY_PROVIDER_USER_ID_CONSTRAINT)) {
            return SocialAccountConstraint.LEGACY_PROVIDER_USER_ID;
        }
        if (normalized.contains(USER_PROVIDER_CONSTRAINT)) {
            return SocialAccountConstraint.USER_PROVIDER;
        }
        return SocialAccountConstraint.UNKNOWN;
    }
}
