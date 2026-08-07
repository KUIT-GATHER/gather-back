package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.meeting.entity.MeetingImage;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import com.gather.gather.domain.meeting.service.MeetingImageUrlResolver;
import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.PostNotificationTarget;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationThumbnailResolverTest {

    private static final Long MEETING_ID = 5L;
    private static final Long MEETING_NOTIFICATION_ID = 10L;
    private static final Long POST_NOTIFICATION_ID = 11L;
    private static final String OBJECT_KEY = "meetings/5/representative.jpg";
    private static final String THUMBNAIL_URL = "https://example.com/meetings/5/representative.jpg";

    @Mock private MeetingImageRepository meetingImageRepository;

    @Mock private MeetingImageUrlResolver meetingImageUrlResolver;

    @Mock private User user;

    @InjectMocks private NotificationThumbnailResolver notificationThumbnailResolver;

    @Test
    @DisplayName("모임과 게시글 알림에 모임 대표 이미지 URL을 매핑한다")
    void resolveByNotificationIdMapsMeetingThumbnail() {
        Notification meetingNotification =
                Notification.create(
                        user,
                        NotificationType.MEETING_JOIN_APPROVED,
                        "[모임명] 가입이 승인되었어요.",
                        NotificationTargetType.MEETING,
                        MEETING_ID);

        Notification postNotification =
                Notification.createPost(
                        user,
                        NotificationType.MEETING_POST_CREATED,
                        "[모임명]에 새 게시글이 등록되었어요.",
                        new PostNotificationTarget(20L, MEETING_ID));

        ReflectionTestUtils.setField(meetingNotification, "id", MEETING_NOTIFICATION_ID);
        ReflectionTestUtils.setField(postNotification, "id", POST_NOTIFICATION_ID);

        MeetingImage meetingImage = MeetingImage.create(MEETING_ID, OBJECT_KEY, 0);

        when(meetingImageRepository.findRepresentativeImagesByMeetingIds(anyCollection()))
                .thenReturn(List.of(meetingImage));

        when(meetingImageUrlResolver.resolve(OBJECT_KEY)).thenReturn(THUMBNAIL_URL);

        Map<Long, String> result =
                notificationThumbnailResolver.resolveByNotificationId(
                        List.of(meetingNotification, postNotification));

        assertThat(result)
                .containsEntry(MEETING_NOTIFICATION_ID, THUMBNAIL_URL)
                .containsEntry(POST_NOTIFICATION_ID, THUMBNAIL_URL);
    }

    @Test
    @DisplayName("봉사공고 알림은 현재 대표 이미지가 없어 썸네일을 반환하지 않는다")
    void resolveByNotificationIdSkipsPostingTarget() {
        Notification postingNotification =
                Notification.create(
                        user,
                        NotificationType.VOLUNTEER_SCHEDULE,
                        "[공고명] 봉사가 내일 진행돼요.",
                        NotificationTargetType.POSTING,
                        30L);

        ReflectionTestUtils.setField(postingNotification, "id", MEETING_NOTIFICATION_ID);

        Map<Long, String> result =
                notificationThumbnailResolver.resolveByNotificationId(List.of(postingNotification));

        assertThat(result).isEmpty();

        verifyNoInteractions(meetingImageRepository, meetingImageUrlResolver);
    }

    @Test
    @DisplayName("모임에 등록된 이미지가 없으면 썸네일을 반환하지 않는다")
    void resolveByNotificationIdReturnsEmptyWhenMeetingHasNoImage() {
        Notification notification =
                Notification.create(
                        user,
                        NotificationType.MEETING_JOIN_APPROVED,
                        "[모임명] 가입이 승인되었어요.",
                        NotificationTargetType.MEETING,
                        MEETING_ID);

        ReflectionTestUtils.setField(notification, "id", MEETING_NOTIFICATION_ID);

        when(meetingImageRepository.findRepresentativeImagesByMeetingIds(anyCollection()))
                .thenReturn(List.of());

        Map<Long, String> result =
                notificationThumbnailResolver.resolveByNotificationId(List.of(notification));

        assertThat(result).isEmpty();

        verifyNoInteractions(meetingImageUrlResolver);
    }
}
