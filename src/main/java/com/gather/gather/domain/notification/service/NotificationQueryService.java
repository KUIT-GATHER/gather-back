package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.notification.dto.NotificationResponse;
import com.gather.gather.domain.notification.dto.NotificationUnreadCountResponse;
import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import com.gather.gather.domain.notification.repository.NotificationRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public PageResponse<NotificationResponse> getNotifications(
            NotificationCategory category, Pageable pageable) {

        Long userId = SecurityUtil.getCurrentUserId();

        Page<NotificationResponse> responses =
                notificationRepository
                        .findAllByUser_IdAndCategoryAndDeletedAtIsNull(userId, category, pageable)
                        .map(NotificationResponse::from);

        return PageResponse.from(responses);
    }

    public NotificationUnreadCountResponse getUnreadCount() {
        Long userId = SecurityUtil.getCurrentUserId();

        long activityCount =
                notificationRepository.countByUser_IdAndCategoryAndReadAtIsNullAndDeletedAtIsNull(
                        userId, NotificationCategory.ACTIVITY);

        long meetingCount =
                notificationRepository.countByUser_IdAndCategoryAndReadAtIsNullAndDeletedAtIsNull(
                        userId, NotificationCategory.MEETING);

        return NotificationUnreadCountResponse.of(activityCount, meetingCount);
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Notification notification = getOwnedNotification(notificationId, userId);

        notification.markAsRead();

        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllAsRead(NotificationCategory category) {
        Long userId = SecurityUtil.getCurrentUserId();

        notificationRepository.markAllAsRead(userId, category, LocalDateTime.now());
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        Long userId = SecurityUtil.getCurrentUserId();
        Notification notification = getOwnedNotification(notificationId, userId);

        notification.delete();
    }

    private Notification getOwnedNotification(Long notificationId, Long userId) {

        return notificationRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    }
}
