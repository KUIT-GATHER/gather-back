package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.PostNotificationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostCommentNotificationService {

    private static final String MESSAGE_FORMAT = "[%s] 작성한 글에 새 댓글이 달렸어요.";

    private final NotificationSettingService notificationSettingService;
    private final NotificationWriter notificationWriter;

    public void createNotification(
            Long recipientUserId, Long meetingId, Long postId, String meetingName) {

        if (!notificationSettingService.isMeetingPostCommentEnabled(recipientUserId)) {
            return;
        }

        String message = MESSAGE_FORMAT.formatted(meetingName);
        PostNotificationTarget target = new PostNotificationTarget(postId, meetingId);

        notificationWriter.createPost(
                recipientUserId, NotificationType.MEETING_POST_COMMENT, message, target);
    }
}
