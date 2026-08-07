package com.gather.gather.domain.notification.event;

public record PostCommentNotificationRequestedEvent(
        Long recipientUserId, Long meetingId, Long postId, String meetingName) {}
