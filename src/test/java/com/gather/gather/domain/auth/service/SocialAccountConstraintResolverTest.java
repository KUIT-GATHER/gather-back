package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLIntegrityConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SocialAccountConstraintResolverTest {

    private final SocialAccountConstraintResolver resolver = new SocialAccountConstraintResolver();

    @Test
    @DisplayName("MySQL 1062 신규·legacy provider 식별자 충돌과 User provider 충돌을 구분한다")
    void resolve_mysqlDuplicateKey_distinguishesConstraints() {
        assertThat(
                        resolver.resolve(
                                mysqlDuplicate(
                                        SocialAccountConstraintResolver.PROVIDER_KEY_CONSTRAINT)))
                .isEqualTo(SocialAccountConstraint.PROVIDER_USER_KEY);
        assertThat(
                        resolver.resolve(
                                mysqlDuplicate(
                                        SocialAccountConstraintResolver
                                                .LEGACY_PROVIDER_USER_ID_CONSTRAINT)))
                .isEqualTo(SocialAccountConstraint.LEGACY_PROVIDER_USER_ID);
        assertThat(
                        resolver.resolve(
                                mysqlDuplicate(
                                        SocialAccountConstraintResolver.USER_PROVIDER_CONSTRAINT)))
                .isEqualTo(SocialAccountConstraint.USER_PROVIDER);
    }

    @Test
    @DisplayName("Hibernate ConstraintViolationException의 constraintName을 우선 분류한다")
    void resolve_hibernateConstraintViolation_usesConstraintName() {
        SQLIntegrityConstraintViolationException sqlException =
                new SQLIntegrityConstraintViolationException(
                        "duplicate without constraint name", "23000", 1062);
        ConstraintViolationException hibernateException =
                new ConstraintViolationException(
                        "could not execute statement",
                        sqlException,
                        SocialAccountConstraintResolver.PROVIDER_KEY_CONSTRAINT);
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("integrity violation", hibernateException);

        assertThat(resolver.resolve(exception))
                .isEqualTo(SocialAccountConstraint.PROVIDER_USER_KEY);
    }

    @Test
    @DisplayName("SocialAccount 제약 이름이 없는 무결성 오류는 UNKNOWN으로 유지한다")
    void resolve_unrelatedIntegrityViolation_isUnknown() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "foreign key failure",
                        new SQLIntegrityConstraintViolationException(
                                "foreign key failure", "23000", 1452));

        assertThat(resolver.resolve(exception)).isEqualTo(SocialAccountConstraint.UNKNOWN);
    }

    private DataIntegrityViolationException mysqlDuplicate(String constraint) {
        return new DataIntegrityViolationException(
                "duplicate",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry for key 'social_account." + constraint + "'",
                        "23000",
                        1062));
    }
}
