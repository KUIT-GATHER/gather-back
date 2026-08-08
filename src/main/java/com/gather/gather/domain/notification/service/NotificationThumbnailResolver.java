package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.meeting.service.MeetingThumbnailResolver;
import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationThumbnailResolver {

    private final MeetingThumbnailResolver meetingThumbnailResolver;

    public Map<Long, String> resolveByNotificationId(Collection<Notification> notifications) {

        Map<Long, Long> meetingIdByNotificationId =
                notifications.stream()
                        .filter(notification -> resolveMeetingId(notification) != null)
                        .collect(Collectors.toMap(Notification::getId, this::resolveMeetingId));

        if (meetingIdByNotificationId.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> thumbnailUrlByMeetingId =
                meetingThumbnailResolver.resolve(meetingIdByNotificationId.values());

        return meetingIdByNotificationId.entrySet().stream()
                .filter(entry -> thumbnailUrlByMeetingId.containsKey(entry.getValue()))
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> thumbnailUrlByMeetingId.get(entry.getValue())));
    }

    private Long resolveMeetingId(Notification notification) {
        if (notification.getTargetType() == NotificationTargetType.MEETING) {
            return notification.getTargetId();
        }

        if (notification.getTargetType() == NotificationTargetType.POST) {
            return notification.getTargetMeetingId();
        }

        return null;
    }
}
