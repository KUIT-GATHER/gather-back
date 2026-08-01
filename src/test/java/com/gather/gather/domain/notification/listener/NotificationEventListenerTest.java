package com.gather.gather.domain.notification.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.notification.event.MeetingJoinResultNotificationRequestedEvent;
import com.gather.gather.domain.notification.service.NotificationCreateService;
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
}
