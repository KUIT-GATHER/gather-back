package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCreateServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationSettingService notificationSettingService;
    @Mock private User recipient;

    @InjectMocks private NotificationCreateService notificationCreateService;

    @Test
    @DisplayName("수신자와 알림 정보를 저장한다")
    void create_savesNotification() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(recipient));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification notification =
                notificationCreateService.create(
                        1L,
                        NotificationType.VOLUNTEER_SCHEDULE,
                        "[공고명] 봉사가 내일 진행돼요.",
                        NotificationTargetType.POSTING,
                        10L);

        assertThat(notification.getUser()).isSameAs(recipient);
        assertThat(notification.getCategory()).isEqualTo(NotificationCategory.ACTIVITY);
        assertThat(notification.getTargetId()).isEqualTo(10L);
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("여러 수신자의 알림을 한 번에 저장한다")
    void createAll_savesNotificationsForRecipients() {
        User firstRecipient = org.mockito.Mockito.mock(User.class);
        User secondRecipient = org.mockito.Mockito.mock(User.class);

        when(userRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(firstRecipient, secondRecipient));

        notificationCreateService.createAll(
                List.of(1L, 2L),
                NotificationType.MEETING_POST_CREATED,
                "[한강공원 플로깅팀]에 팀원님이 새 게시글을 등록했어요.",
                NotificationTargetType.POST,
                10L,
                20L);

        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("알림 수신자가 없으면 사용자와 알림을 저장하지 않는다")
    void createAll_doesNothingWhenRecipientsAreEmpty() {
        notificationCreateService.createAll(
                List.of(),
                NotificationType.MEETING_POST_CREATED,
                "[한강공원 플로깅팀]에 팀원님이 새 게시글을 등록했어요.",
                NotificationTargetType.POST,
                10L,
                20L);

        verify(userRepository, never()).findAllById(anyList());
        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("수신자가 존재하지 않으면 알림을 저장하지 않는다")
    void create_rejectsMissingRecipient() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                notificationCreateService.create(
                                        1L,
                                        NotificationType.VOLUNTEER_SCHEDULE,
                                        "[공고명] 봉사가 내일 진행돼요.",
                                        NotificationTargetType.POSTING,
                                        10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
