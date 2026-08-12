package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.notification.dto.NotificationResponse;
import com.gather.gather.domain.notification.dto.NotificationUnreadCountResponse;
import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long NOTIFICATION_ID = 10L;
    private static final Instant NOW_INSTANT = Instant.parse("2026-08-11T07:09:09Z");
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(NOW_INSTANT, ZoneOffset.UTC);

    @Mock private NotificationRepository notificationRepository;
    @Mock private User user;
    @Mock private NotificationThumbnailResolver notificationThumbnailResolver;

    private NotificationQueryService notificationQueryService;

    @BeforeEach
    void setUp() {
        notificationQueryService =
                new NotificationQueryService(
                        notificationRepository,
                        notificationThumbnailResolver,
                        Clock.fixed(NOW_INSTANT, ZoneOffset.UTC));

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("로그인 사용자의 카테고리별 알림을 조회한다")
    void getNotifications_returnsCategoryNotifications() {
        Notification notification = createNotification();

        when(notificationRepository.findAllByUser_IdAndCategoryAndDeletedAtIsNull(
                        USER_ID, NotificationCategory.MEETING, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1));

        when(notificationThumbnailResolver.resolveByNotificationId(anyCollection()))
                .thenReturn(Map.of(NOTIFICATION_ID, "https://example.com/meeting-thumbnail.jpg"));

        PageResponse<NotificationResponse> response =
                notificationQueryService.getNotifications(
                        NotificationCategory.MEETING, PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo(NOTIFICATION_ID);
        assertThat(response.content().get(0).read()).isFalse();
        assertThat(response.content().get(0).createdAt()).isEqualTo(NOW_INSTANT);
    }

    @Test
    @DisplayName("본인의 알림을 읽음 처리한다")
    void markAsRead_marksOwnedNotification() {
        Notification notification = createNotification();

        when(notificationRepository.findByIdAndUser_IdAndDeletedAtIsNull(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.of(notification));

        when(notificationThumbnailResolver.resolveByNotificationId(anyCollection()))
                .thenReturn(Map.of(NOTIFICATION_ID, "https://example.com/meeting-thumbnail.jpg"));

        NotificationResponse response = notificationQueryService.markAsRead(NOTIFICATION_ID);

        assertThat(response.read()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("현재 카테고리의 모든 알림을 읽음 처리한다")
    void markAllAsRead_updatesCategoryNotifications() {
        notificationQueryService.markAllAsRead(NotificationCategory.ACTIVITY);

        verify(notificationRepository).markAllAsRead(USER_ID, NotificationCategory.ACTIVITY, NOW);
    }

    @Test
    @DisplayName("본인의 알림을 삭제한다")
    void deleteNotification_deletesOwnedNotification() {
        Notification notification = createNotification();

        when(notificationRepository.findByIdAndUser_IdAndDeletedAtIsNull(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.of(notification));

        notificationQueryService.deleteNotification(NOTIFICATION_ID);

        assertThat(notification.getDeletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("존재하지 않거나 다른 사용자의 알림은 변경할 수 없다")
    void markAsRead_rejectsUnownedNotification() {
        when(notificationRepository.findByIdAndUser_IdAndDeletedAtIsNull(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationQueryService.markAsRead(NOTIFICATION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Notification createNotification() {
        Notification notification =
                Notification.create(
                        user,
                        NotificationType.MEETING_JOIN_APPROVED,
                        "[모임명] 가입이 승인되었어요.",
                        NotificationTargetType.MEETING,
                        5L);

        ReflectionTestUtils.setField(notification, "id", NOTIFICATION_ID);
        ReflectionTestUtils.setField(notification, "createdAt", NOW);

        return notification;
    }

    @Test
    @DisplayName("활동과 모임의 미읽음 알림 개수를 조회한다")
    void getUnreadCountReturnsCategoryAndTotalCounts() {
        // given
        when(notificationRepository.countByUser_IdAndCategoryAndReadAtIsNullAndDeletedAtIsNull(
                        USER_ID, NotificationCategory.ACTIVITY))
                .thenReturn(2L);

        when(notificationRepository.countByUser_IdAndCategoryAndReadAtIsNullAndDeletedAtIsNull(
                        USER_ID, NotificationCategory.MEETING))
                .thenReturn(3L);

        // when
        NotificationUnreadCountResponse response = notificationQueryService.getUnreadCount();

        // then
        assertThat(response.activity()).isEqualTo(2L);
        assertThat(response.meeting()).isEqualTo(3L);
        assertThat(response.total()).isEqualTo(5L);

        verify(notificationRepository)
                .countByUser_IdAndCategoryAndReadAtIsNullAndDeletedAtIsNull(
                        USER_ID, NotificationCategory.ACTIVITY);
        verify(notificationRepository)
                .countByUser_IdAndCategoryAndReadAtIsNullAndDeletedAtIsNull(
                        USER_ID, NotificationCategory.MEETING);
    }
}
