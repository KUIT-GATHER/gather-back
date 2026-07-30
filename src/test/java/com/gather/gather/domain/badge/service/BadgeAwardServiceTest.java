package com.gather.gather.domain.badge.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.entity.UserBadge;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.service.NotificationCreateService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BadgeAwardServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private NotificationCreateService notificationCreateService;

    private BadgeAwardService badgeAwardService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        badgeAwardService = new BadgeAwardService(userBadgeRepository, notificationCreateService);
    }

    @Test
    @DisplayName("award saves a new badge and sends a notification when not already earned")
    void award_savesAndNotifies_whenNotAlreadyAwarded() {
        when(userBadgeRepository.existsByUserIdAndBadgeType(USER_ID, BadgeType.FIRST_COMPLETION))
                .thenReturn(false);

        badgeAwardService.award(USER_ID, BadgeType.FIRST_COMPLETION);

        verify(userBadgeRepository).saveAndFlush(any(UserBadge.class));
        verify(notificationCreateService)
                .create(
                        eq(USER_ID),
                        eq(NotificationType.BADGE_EARNED),
                        any(String.class),
                        eq(NotificationTargetType.MY_PAGE),
                        eq(null));
    }

    @Test
    @DisplayName("award does nothing when the badge was already earned")
    void award_doesNothing_whenAlreadyAwarded() {
        when(userBadgeRepository.existsByUserIdAndBadgeType(USER_ID, BadgeType.FIRST_COMPLETION))
                .thenReturn(true);

        badgeAwardService.award(USER_ID, BadgeType.FIRST_COMPLETION);

        verify(userBadgeRepository, never()).saveAndFlush(any());
        verify(notificationCreateService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("award swallows a unique constraint race and skips the notification")
    void award_swallowsRace_whenUniqueConstraintViolated() {
        when(userBadgeRepository.existsByUserIdAndBadgeType(USER_ID, BadgeType.FIRST_COMPLETION))
                .thenReturn(false);
        DataIntegrityViolationException dbException =
                new DataIntegrityViolationException(
                        "duplicate",
                        new ConstraintViolationException("dup", null, "uq_user_badge_user_type"));
        when(userBadgeRepository.saveAndFlush(any(UserBadge.class))).thenThrow(dbException);

        badgeAwardService.award(USER_ID, BadgeType.FIRST_COMPLETION);

        verify(notificationCreateService, never()).create(any(), any(), any(), any(), any());
    }
}
