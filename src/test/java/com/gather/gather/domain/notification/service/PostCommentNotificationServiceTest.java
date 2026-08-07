package com.gather.gather.domain.notification.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.PostNotificationTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostCommentNotificationServiceTest {

    private static final Long RECIPIENT_USER_ID = 1L;
    private static final Long MEETING_ID = 10L;
    private static final Long POST_ID = 100L;
    private static final String MEETING_NAME = "한강공원 플로깅";

    @Mock private NotificationSettingService notificationSettingService;

    @Mock private NotificationWriter notificationWriter;

    @InjectMocks private PostCommentNotificationService postCommentNotificationService;

    @Test
    @DisplayName("댓글 알림 설정이 활성화되어 있으면 게시글 작성자에게 알림을 생성한다")
    void createNotificationCreatesNotificationWhenEnabled() {
        when(notificationSettingService.isMeetingPostCommentEnabled(RECIPIENT_USER_ID))
                .thenReturn(true);

        postCommentNotificationService.createNotification(
                RECIPIENT_USER_ID, MEETING_ID, POST_ID, MEETING_NAME);

        verify(notificationWriter)
                .createPost(
                        RECIPIENT_USER_ID,
                        NotificationType.MEETING_POST_COMMENT,
                        "[한강공원 플로깅] 작성한 글에 새 댓글이 달렸어요.",
                        new PostNotificationTarget(POST_ID, MEETING_ID));
    }

    @Test
    @DisplayName("댓글 알림 설정이 비활성화되어 있으면 알림을 생성하지 않는다")
    void createNotificationDoesNotCreateNotificationWhenDisabled() {
        when(notificationSettingService.isMeetingPostCommentEnabled(RECIPIENT_USER_ID))
                .thenReturn(false);

        postCommentNotificationService.createNotification(
                RECIPIENT_USER_ID, MEETING_ID, POST_ID, MEETING_NAME);

        verify(notificationWriter, never())
                .createPost(
                        RECIPIENT_USER_ID,
                        NotificationType.MEETING_POST_COMMENT,
                        "[한강공원 플로깅] 작성한 글에 새 댓글이 달렸어요.",
                        new PostNotificationTarget(POST_ID, MEETING_ID));
    }
}
