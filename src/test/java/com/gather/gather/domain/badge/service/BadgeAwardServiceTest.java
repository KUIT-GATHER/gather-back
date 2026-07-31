package com.gather.gather.domain.badge.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.domain.notification.entity.NotificationSetting;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.domain.notification.service.NotificationCreateService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BadgeAwardServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private UserBadgeWriter userBadgeWriter;
    @Mock private NotificationCreateService notificationCreateService;
    @Mock private NotificationSettingRepository notificationSettingRepository;

    private BadgeAwardService badgeAwardService;

    @BeforeEach
    void setUp() {
        badgeAwardService =
                new BadgeAwardService(
                        userBadgeRepository,
                        userBadgeWriter,
                        notificationCreateService,
                        notificationSettingRepository);
    }

    @Test
    @DisplayName(
            "award saves a new badge and sends a notification when badge notifications are enabled")
    void award_savesAndNotifies_whenNotAlreadyAwardedAndNotificationEnabled() {
        when(userBadgeRepository.existsByUserIdAndBadgeType(USER_ID, BadgeType.FIRST_COMPLETION))
                .thenReturn(false);
        when(userBadgeWriter.tryInsert(USER_ID, BadgeType.FIRST_COMPLETION)).thenReturn(true);
        when(notificationSettingRepository.findByUser_Id(USER_ID))
                .thenReturn(Optional.of(badgeEnabledSetting(true)));

        badgeAwardService.award(USER_ID, BadgeType.FIRST_COMPLETION);

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

        verify(userBadgeWriter, never()).tryInsert(any(), any());
        verify(notificationCreateService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName(
            "award skips the notification when the writer reports a duplicate-badge race"
                    + " (REQUIRES_NEW isolation, M-1)")
    void award_skipsNotification_whenWriterReportsDuplicate() {
        when(userBadgeRepository.existsByUserIdAndBadgeType(USER_ID, BadgeType.FIRST_COMPLETION))
                .thenReturn(false);
        when(userBadgeWriter.tryInsert(USER_ID, BadgeType.FIRST_COMPLETION)).thenReturn(false);

        badgeAwardService.award(USER_ID, BadgeType.FIRST_COMPLETION);

        verify(notificationCreateService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName(
            "award keeps the saved badge even when sending the earned-badge notification fails")
    void award_keepsBadge_whenNotificationCreationFails() {
        when(userBadgeRepository.existsByUserIdAndBadgeType(USER_ID, BadgeType.FIRST_COMPLETION))
                .thenReturn(false);
        when(userBadgeWriter.tryInsert(USER_ID, BadgeType.FIRST_COMPLETION)).thenReturn(true);
        when(notificationSettingRepository.findByUser_Id(USER_ID))
                .thenReturn(Optional.of(badgeEnabledSetting(true)));
        when(notificationCreateService.create(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("recipient user not found"));

        badgeAwardService.award(USER_ID, BadgeType.FIRST_COMPLETION);

        verify(userBadgeWriter).tryInsert(USER_ID, BadgeType.FIRST_COMPLETION);
    }

    @Test
    @DisplayName("award skips the notification when the user disabled badge notifications (M-6)")
    void award_skipsNotification_whenBadgeNotificationDisabled() {
        when(userBadgeRepository.existsByUserIdAndBadgeType(USER_ID, BadgeType.FIRST_COMPLETION))
                .thenReturn(false);
        when(userBadgeWriter.tryInsert(USER_ID, BadgeType.FIRST_COMPLETION)).thenReturn(true);
        when(notificationSettingRepository.findByUser_Id(USER_ID))
                .thenReturn(Optional.of(badgeEnabledSetting(false)));

        badgeAwardService.award(USER_ID, BadgeType.FIRST_COMPLETION);

        verify(notificationCreateService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName(
            "award skips the notification when the user has no NotificationSetting row (defaults"
                    + " to disabled, M-6)")
    void award_skipsNotification_whenNoNotificationSettingExists() {
        when(userBadgeRepository.existsByUserIdAndBadgeType(USER_ID, BadgeType.FIRST_COMPLETION))
                .thenReturn(false);
        when(userBadgeWriter.tryInsert(USER_ID, BadgeType.FIRST_COMPLETION)).thenReturn(true);
        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());

        badgeAwardService.award(USER_ID, BadgeType.FIRST_COMPLETION);

        verify(notificationCreateService, never()).create(any(), any(), any(), any(), any());
    }

    private NotificationSetting badgeEnabledSetting(boolean badgeEnabled) {
        NotificationSetting setting = NotificationSetting.createDefault(null);
        ReflectionTestUtils.setField(setting, "badgeEnabled", badgeEnabled);
        return setting;
    }
}
