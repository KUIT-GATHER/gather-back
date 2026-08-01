package com.gather.gather.domain.notification.event;

public record MeetingJoinResultNotificationRequestedEvent(
        Long recipientUserId, Long meetingId, String meetingName, boolean approved) {}
