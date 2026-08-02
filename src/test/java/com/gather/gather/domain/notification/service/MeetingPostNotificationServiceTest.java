package com.gather.gather.domain.notification.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.PostNotificationTarget;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingPostNotificationServiceTest {

    private static final Long MEETING_ID = 40L;
    private static final Long POST_ID = 30L;
    private static final Long AUTHOR_ID = 1L;
    private static final String MESSAGE = "[모임명]에 작성자님이 새 게시글을 등록했어요.";

    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private NotificationWriter notificationWriter;

    @InjectMocks private MeetingPostNotificationService meetingPostNotificationService;

    @Test
    @DisplayName("작성자와 비활성 사용자를 제외하고 중복 없는 활성 모임원에게 알림을 생성한다")
    void createNotificationsFiltersRecipients() {
        MeetingMember author = member(AUTHOR_ID, UserStatus.ACTIVE);
        MeetingMember activeRecipient = member(2L, UserStatus.ACTIVE);
        MeetingMember duplicateRecipient = member(2L, UserStatus.ACTIVE);
        MeetingMember withdrawnRecipient = member(3L, UserStatus.WITHDRAWN);
        when(meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                        MEETING_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(
                        List.of(author, activeRecipient, duplicateRecipient, withdrawnRecipient));

        meetingPostNotificationService.createNotifications(
                MEETING_ID, POST_ID, AUTHOR_ID, NotificationType.MEETING_POST_CREATED, MESSAGE);

        verify(notificationWriter)
                .createPost(
                        2L,
                        NotificationType.MEETING_POST_CREATED,
                        MESSAGE,
                        new PostNotificationTarget(POST_ID, MEETING_ID));
        verify(notificationWriter, never())
                .createPost(
                        1L,
                        NotificationType.MEETING_POST_CREATED,
                        MESSAGE,
                        new PostNotificationTarget(POST_ID, MEETING_ID));
        verify(notificationWriter, never())
                .createPost(
                        3L,
                        NotificationType.MEETING_POST_CREATED,
                        MESSAGE,
                        new PostNotificationTarget(POST_ID, MEETING_ID));
    }

    @Test
    @DisplayName("한 수신자의 알림 생성이 실패해도 나머지 수신자 알림은 계속 생성한다")
    void createNotificationsContinuesAfterIndividualFailure() {
        MeetingMember firstRecipient = member(2L, UserStatus.ACTIVE);
        MeetingMember secondRecipient = member(3L, UserStatus.ACTIVE);
        when(meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                        MEETING_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of(firstRecipient, secondRecipient));
        PostNotificationTarget target = new PostNotificationTarget(POST_ID, MEETING_ID);
        doThrow(new IllegalStateException("recipient missing"))
                .when(notificationWriter)
                .createPost(2L, NotificationType.MEETING_POST_CREATED, MESSAGE, target);

        meetingPostNotificationService.createNotifications(
                MEETING_ID, POST_ID, AUTHOR_ID, NotificationType.MEETING_POST_CREATED, MESSAGE);

        verify(notificationWriter)
                .createPost(2L, NotificationType.MEETING_POST_CREATED, MESSAGE, target);
        verify(notificationWriter)
                .createPost(3L, NotificationType.MEETING_POST_CREATED, MESSAGE, target);
    }

    @Test
    @DisplayName("작성자만 있는 모임이면 알림 저장을 시도하지 않는다")
    void createNotificationsDoesNothingWhenOnlyAuthorExists() {
        MeetingMember author = member(AUTHOR_ID, UserStatus.ACTIVE);
        when(meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                        MEETING_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of(author));

        meetingPostNotificationService.createNotifications(
                MEETING_ID, POST_ID, AUTHOR_ID, NotificationType.MEETING_POST_CREATED, MESSAGE);

        verifyNoInteractions(notificationWriter);
    }

    private MeetingMember member(Long userId, UserStatus status) {
        User user = mock(User.class);
        org.mockito.Mockito.lenient().when(user.getId()).thenReturn(userId);
        org.mockito.Mockito.lenient().when(user.getStatus()).thenReturn(status);
        MeetingMember member = mock(MeetingMember.class);
        when(member.getUser()).thenReturn(user);
        return member;
    }
}
