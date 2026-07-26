package com.gather.gather.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    VOLUNTEER_SCHEDULE(NotificationCategory.ACTIVITY),
    BOOKMARKED_POSTING_DEADLINE(NotificationCategory.ACTIVITY),
    BADGE_EARNED(NotificationCategory.ACTIVITY),
    MEETING_JOIN_APPROVED(NotificationCategory.MEETING),
    MEETING_JOIN_REJECTED(NotificationCategory.MEETING),
    MEETING_POST_COMMENT(NotificationCategory.MEETING),
    MEETING_NOTICE_CREATED(NotificationCategory.MEETING),
    MEETING_POSTING_CREATED(NotificationCategory.MEETING),
    MEETING_POST_CREATED(NotificationCategory.MEETING);

    private final NotificationCategory category;
}
