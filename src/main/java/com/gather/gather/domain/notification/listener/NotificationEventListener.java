package com.gather.gather.domain.notification.listener;

import com.gather.gather.domain.notification.event.MeetingJoinResultNotificationRequestedEvent;
import com.gather.gather.domain.notification.event.MeetingPostNotificationRequestedEvent;
import com.gather.gather.domain.notification.event.PostCommentNotificationRequestedEvent;
import com.gather.gather.domain.notification.service.MeetingPostNotificationService;
import com.gather.gather.domain.notification.service.NotificationCreateService;
import com.gather.gather.domain.notification.service.PostCommentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationCreateService notificationCreateService;
    private final MeetingPostNotificationService meetingPostNotificationService;
    private final PostCommentNotificationService postCommentNotificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingJoinResultNotificationRequested(
            MeetingJoinResultNotificationRequestedEvent event) {
        try {
            notificationCreateService.createMeetingJoinResultNotification(
                    event.recipientUserId(),
                    event.meetingId(),
                    event.meetingName(),
                    event.approved());
        } catch (RuntimeException exception) {
            log.warn(
                    "모임 가입 결과 알림 생성 실패(가입 처리는 이미 커밋되어 유지됨). userId={}, meetingId={}, approved={}",
                    event.recipientUserId(),
                    event.meetingId(),
                    event.approved(),
                    exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingPostNotificationRequested(MeetingPostNotificationRequestedEvent event) {
        try {
            meetingPostNotificationService.createNotifications(
                    event.meetingId(),
                    event.postId(),
                    event.authorId(),
                    event.type(),
                    event.message());
        } catch (RuntimeException exception) {
            log.warn(
                    "모임 게시글 알림 처리 실패(게시글은 이미 커밋되어 유지됨). postId={}, meetingId={}, authorId={}",
                    event.postId(),
                    event.meetingId(),
                    event.authorId(),
                    exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCommentNotificationRequested(PostCommentNotificationRequestedEvent event) {
        try {
            postCommentNotificationService.createNotification(
                    event.recipientUserId(),
                    event.meetingId(),
                    event.postId(),
                    event.meetingName());
        } catch (RuntimeException exception) {
            log.warn(
                    "댓글 알림 생성 실패(댓글은 이미 커밋되어 유지됨). "
                            + "recipientUserId={}, meetingId={}, postId={}",
                    event.recipientUserId(),
                    event.meetingId(),
                    event.postId(),
                    exception);
        }
    }
}
