package com.gather.gather.domain.badge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.entity.UserBadge;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserBadgeWriterTest {

    private static final Long USER_ID = 1L;

    @Mock private UserBadgeRepository userBadgeRepository;

    private UserBadgeWriter userBadgeWriter;

    @BeforeEach
    void setUp() {
        userBadgeWriter = new UserBadgeWriter(userBadgeRepository);
    }

    @Test
    @DisplayName("tryInsert returns true when the badge is saved without conflict")
    void tryInsert_returnsTrue_whenSaveSucceeds() {
        boolean result = userBadgeWriter.tryInsert(USER_ID, BadgeType.FIRST_COMPLETION);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("tryInsert returns false and swallows a unique constraint race")
    void tryInsert_returnsFalse_whenUniqueConstraintViolated() {
        DataIntegrityViolationException dbException =
                new DataIntegrityViolationException(
                        "duplicate",
                        new ConstraintViolationException("dup", null, "uq_user_badge_user_type"));
        when(userBadgeRepository.saveAndFlush(any(UserBadge.class))).thenThrow(dbException);

        boolean result = userBadgeWriter.tryInsert(USER_ID, BadgeType.FIRST_COMPLETION);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("tryInsert rethrows when the violated constraint is not the badge uniqueness one")
    void tryInsert_rethrows_whenConstraintViolationIsNotUniqueBadge() {
        DataIntegrityViolationException fkViolation =
                new DataIntegrityViolationException(
                        "fk violation",
                        new ConstraintViolationException("fk", null, "fk_user_badge_user"));
        when(userBadgeRepository.saveAndFlush(any(UserBadge.class))).thenThrow(fkViolation);

        assertThatThrownBy(() -> userBadgeWriter.tryInsert(USER_ID, BadgeType.FIRST_COMPLETION))
                .isSameAs(fkViolation);
    }
}
