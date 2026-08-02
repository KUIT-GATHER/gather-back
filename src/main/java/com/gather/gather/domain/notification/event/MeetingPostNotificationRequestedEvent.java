package com.gather.gather.domain.notification.event;

import com.gather.gather.domain.notification.enums.NotificationType;

public record MeetingPostNotificationRequestedEvent(
        Long meetingId, Long postId, Long authorId, NotificationType type, String message) {}
