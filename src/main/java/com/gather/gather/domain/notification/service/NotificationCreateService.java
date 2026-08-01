package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationCreateService {

    private final NotificationWriter notificationWriter;
    private final NotificationSettingService notificationSettingService;

    public Notification create(
            Long recipientUserId,
            NotificationType type,
            String message,
            NotificationTargetType targetType,
            Long targetId) {
        return notificationWriter.create(recipientUserId, type, message, targetType, targetId);
    }

    public void createMeetingJoinResultNotification(
            Long recipientUserId, Long meetingId, String meetingName, boolean approved) {

        if (!notificationSettingService.isMeetingJoinResultEnabled(recipientUserId)) {
            return;
        }

        NotificationType type =
                approved
                        ? NotificationType.MEETING_JOIN_APPROVED
                        : NotificationType.MEETING_JOIN_REJECTED;

        String message =
                approved
                        ? "[%s] 가입이 승인되었어요. 지금부터 모임 활동에 참여할 수 있어요.".formatted(meetingName)
                        : "[%s] 가입이 거절되었어요. 다른 모임을 찾아보세요.".formatted(meetingName);

        notificationWriter.create(
                recipientUserId, type, message, NotificationTargetType.MEETING, meetingId);
    }
}
