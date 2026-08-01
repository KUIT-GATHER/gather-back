package com.gather.gather.domain.notification.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.event.MeetingJoinResultNotificationRequestedEvent;
import com.gather.gather.domain.notification.event.MeetingPostNotificationRequestedEvent;
import com.gather.gather.domain.notification.service.NotificationCreateService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationCreateService notificationCreateService;

    @InjectMocks private NotificationEventListener notificationEventListener;

    @Test
    @DisplayName("가입 처리 커밋 후 가입 결과 알림을 생성한다")
    void onMeetingJoinResultNotificationRequestedCreatesNotification() {
        MeetingJoinResultNotificationRequestedEvent event =
                new MeetingJoinResultNotificationRequestedEvent(1L, 2L, "한강공원 플로깅팀", true);

        notificationEventListener.onMeetingJoinResultNotificationRequested(event);

        verify(notificationCreateService)
                .createMeetingJoinResultNotification(1L, 2L, "한강공원 플로깅팀", true);
    }

    @Test
    @DisplayName("알림 생성 실패를 가입 처리로 전파하지 않는다")
    void onMeetingJoinResultNotificationRequestedDoesNotPropagateFailure() {
        MeetingJoinResultNotificationRequestedEvent event =
                new MeetingJoinResultNotificationRequestedEvent(1L, 2L, "한강공원 플로깅팀", true);
        doThrow(new IllegalStateException("notification failed"))
                .when(notificationCreateService)
                .createMeetingJoinResultNotification(1L, 2L, "한강공원 플로깅팀", true);

        assertThatCode(
                        () ->
                                notificationEventListener.onMeetingJoinResultNotificationRequested(
                                        event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("게시글 저장 커밋 후 모임 게시글 알림을 생성한다")
    void onMeetingPostNotificationRequestedCreatesNotifications() {
        MeetingPostNotificationRequestedEvent event =
                new MeetingPostNotificationRequestedEvent(
                        List.of(1L, 2L),
                        NotificationType.MEETING_POST_CREATED,
                        "[모임명]에 작성자님이 새 게시글을 등록했어요.",
                        30L,
                        40L);

        notificationEventListener.onMeetingPostNotificationRequested(event);

        verify(notificationCreateService)
                .createAll(
                        List.of(1L, 2L),
                        NotificationType.MEETING_POST_CREATED,
                        "[모임명]에 작성자님이 새 게시글을 등록했어요.",
                        30L,
                        40L);
    }

    @Test
    @DisplayName("게시글 알림 생성 실패를 게시글 저장 결과로 전파하지 않는다")
    void onMeetingPostNotificationRequestedDoesNotPropagateFailure() {
        MeetingPostNotificationRequestedEvent event =
                new MeetingPostNotificationRequestedEvent(
                        List.of(1L),
                        NotificationType.MEETING_NOTICE_CREATED,
                        "[모임명]에 새 공지가 등록되었어요.",
                        30L,
                        40L);
        doThrow(new IllegalStateException("notification failed"))
                .when(notificationCreateService)
                .createAll(
                        List.of(1L),
                        NotificationType.MEETING_NOTICE_CREATED,
                        "[모임명]에 새 공지가 등록되었어요.",
                        30L,
                        40L);

        assertThatCode(() -> notificationEventListener.onMeetingPostNotificationRequested(event))
                .doesNotThrowAnyException();
    }
}
