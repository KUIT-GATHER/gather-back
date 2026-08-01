package com.gather.gather.domain.notification.listener;

import com.gather.gather.domain.notification.event.MeetingJoinResultNotificationRequestedEvent;
import com.gather.gather.domain.notification.service.NotificationCreateService;
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
}
