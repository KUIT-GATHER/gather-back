package com.gather.gather.domain.notification.event;

import com.gather.gather.domain.notification.enums.NotificationType;
import java.util.List;

public record MeetingPostNotificationRequestedEvent(
        List<Long> recipientUserIds,
        NotificationType type,
        String message,
        Long postId,
        Long meetingId) {

    public MeetingPostNotificationRequestedEvent {
        recipientUserIds = List.copyOf(recipientUserIds);
    }
}
