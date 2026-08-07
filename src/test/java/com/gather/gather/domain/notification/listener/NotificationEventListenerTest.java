package com.gather.gather.domain.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.event.MeetingJoinResultNotificationRequestedEvent;
import com.gather.gather.domain.notification.event.MeetingPostNotificationRequestedEvent;
import com.gather.gather.domain.notification.event.PostCommentNotificationRequestedEvent;
import com.gather.gather.domain.notification.service.MeetingPostNotificationService;
import com.gather.gather.domain.notification.service.NotificationCreateService;
import com.gather.gather.domain.notification.service.PostCommentNotificationService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationCreateService notificationCreateService;
    @Mock private MeetingPostNotificationService meetingPostNotificationService;
    @Mock private PostCommentNotificationService postCommentNotificationService;

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
                        40L,
                        30L,
                        1L,
                        NotificationType.MEETING_POST_CREATED,
                        "[모임명]에 작성자님이 새 게시글을 등록했어요.");

        notificationEventListener.onMeetingPostNotificationRequested(event);

        verify(meetingPostNotificationService)
                .createNotifications(
                        40L,
                        30L,
                        1L,
                        NotificationType.MEETING_POST_CREATED,
                        "[모임명]에 작성자님이 새 게시글을 등록했어요.");
    }

    @Test
    @DisplayName("게시글 알림 생성 실패를 게시글 저장 결과로 전파하지 않는다")
    void onMeetingPostNotificationRequestedDoesNotPropagateFailure() {
        MeetingPostNotificationRequestedEvent event =
                new MeetingPostNotificationRequestedEvent(
                        40L,
                        30L,
                        1L,
                        NotificationType.MEETING_NOTICE_CREATED,
                        "[모임명]에 새 공지가 등록되었어요.");
        doThrow(new IllegalStateException("notification failed"))
                .when(meetingPostNotificationService)
                .createNotifications(
                        40L,
                        30L,
                        1L,
                        NotificationType.MEETING_NOTICE_CREATED,
                        "[모임명]에 새 공지가 등록되었어요.");

        assertThatCode(() -> notificationEventListener.onMeetingPostNotificationRequested(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("모임 게시글 알림 리스너는 커밋 후에만 실행된다")
    void meetingPostListenerRunsAfterCommit() throws NoSuchMethodException {
        Method listenerMethod =
                NotificationEventListener.class.getMethod(
                        "onMeetingPostNotificationRequested",
                        MeetingPostNotificationRequestedEvent.class);

        TransactionalEventListener annotation =
                listenerMethod.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("댓글 저장 커밋 후 게시글 작성자에게 댓글 알림을 생성한다")
    void onPostCommentNotificationRequestedCreatesNotification() {
        PostCommentNotificationRequestedEvent event =
                new PostCommentNotificationRequestedEvent(1L, 10L, 100L, "한강공원 플로깅");

        notificationEventListener.onPostCommentNotificationRequested(event);

        verify(postCommentNotificationService).createNotification(1L, 10L, 100L, "한강공원 플로깅");
    }

    @Test
    @DisplayName("댓글 알림 생성 실패를 댓글 작성 결과로 전파하지 않는다")
    void onPostCommentNotificationRequestedDoesNotPropagateFailure() {
        PostCommentNotificationRequestedEvent event =
                new PostCommentNotificationRequestedEvent(1L, 10L, 100L, "한강공원 플로깅");

        doThrow(new IllegalStateException("notification failed"))
                .when(postCommentNotificationService)
                .createNotification(1L, 10L, 100L, "한강공원 플로깅");

        assertThatCode(() -> notificationEventListener.onPostCommentNotificationRequested(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("댓글 알림 리스너는 댓글 트랜잭션 커밋 후 실행된다")
    void postCommentListenerRunsAfterCommit() throws NoSuchMethodException {
        Method listenerMethod =
                NotificationEventListener.class.getMethod(
                        "onPostCommentNotificationRequested",
                        PostCommentNotificationRequestedEvent.class);

        TransactionalEventListener annotation =
                listenerMethod.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
