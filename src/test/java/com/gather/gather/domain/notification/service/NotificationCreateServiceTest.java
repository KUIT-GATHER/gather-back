package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCreateServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 10L;
    private static final String MEETING_NAME = "한강공원 플로깅팀";

    @Mock private NotificationWriter notificationWriter;
    @Mock private NotificationSettingService notificationSettingService;
    @Mock private Notification notification;

    @InjectMocks private NotificationCreateService notificationCreateService;

    @Test
    @DisplayName("일반 알림 생성을 별도 트랜잭션 Writer에 위임한다")
    void createDelegatesToWriter() {
        when(notificationWriter.create(
                        USER_ID,
                        NotificationType.VOLUNTEER_SCHEDULE,
                        "봉사가 내일 진행돼요.",
                        NotificationTargetType.POSTING,
                        20L))
                .thenReturn(notification);

        Notification result =
                notificationCreateService.create(
                        USER_ID,
                        NotificationType.VOLUNTEER_SCHEDULE,
                        "봉사가 내일 진행돼요.",
                        NotificationTargetType.POSTING,
                        20L);

        assertThat(result).isSameAs(notification);
    }

    @Test
    @DisplayName("모임 가입 결과 알림 설정이 꺼져 있으면 알림을 생성하지 않는다")
    void createMeetingJoinResultNotificationSkipsWhenDisabled() {
        when(notificationSettingService.isMeetingJoinResultEnabled(USER_ID)).thenReturn(false);

        notificationCreateService.createMeetingJoinResultNotification(
                USER_ID, MEETING_ID, MEETING_NAME, true);

        verifyNoInteractions(notificationWriter);
    }

    @Test
    @DisplayName("가입 승인 알림의 타입과 메시지를 생성한다")
    void createMeetingJoinResultNotificationCreatesApprovedMessage() {
        when(notificationSettingService.isMeetingJoinResultEnabled(USER_ID)).thenReturn(true);

        notificationCreateService.createMeetingJoinResultNotification(
                USER_ID, MEETING_ID, MEETING_NAME, true);

        verify(notificationWriter)
                .create(
                        USER_ID,
                        NotificationType.MEETING_JOIN_APPROVED,
                        "[한강공원 플로깅팀] 가입이 승인되었어요. 지금부터 모임 활동에 참여할 수 있어요.",
                        NotificationTargetType.MEETING,
                        MEETING_ID);
    }

    @Test
    @DisplayName("가입 거절 알림의 타입과 메시지를 생성한다")
    void createMeetingJoinResultNotificationCreatesRejectedMessage() {
        when(notificationSettingService.isMeetingJoinResultEnabled(USER_ID)).thenReturn(true);

        notificationCreateService.createMeetingJoinResultNotification(
                USER_ID, MEETING_ID, MEETING_NAME, false);

        verify(notificationWriter)
                .create(
                        USER_ID,
                        NotificationType.MEETING_JOIN_REJECTED,
                        "[한강공원 플로깅팀] 가입이 거절되었어요. 다른 모임을 찾아보세요.",
                        NotificationTargetType.MEETING,
                        MEETING_ID);
    }
}
