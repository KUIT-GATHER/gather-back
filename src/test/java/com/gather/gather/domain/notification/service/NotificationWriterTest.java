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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class NotificationWriterTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private User recipient;

    @InjectMocks private NotificationWriter notificationWriter;

    @Test
    @DisplayName("수신자와 알림 정보를 저장한다")
    void createSavesNotification() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(recipient));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification notification =
                notificationWriter.create(
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
    @DisplayName("수신자가 존재하지 않으면 알림을 저장하지 않는다")
    void createRejectsMissingRecipient() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                notificationWriter.create(
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

    @Test
    @DisplayName("알림 저장은 항상 새로운 트랜잭션에서 실행된다")
    void createUsesRequiresNewTransaction() throws NoSuchMethodException {
        Transactional transactional =
                NotificationWriter.class
                        .getMethod(
                                "create",
                                Long.class,
                                NotificationType.class,
                                String.class,
                                NotificationTargetType.class,
                                Long.class)
                        .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("중복 수신자를 제거하고 모임 게시글 알림을 일괄 저장한다")
    void createAllSavesNotificationsForDistinctRecipients() {
        User secondRecipient = org.mockito.Mockito.mock(User.class);
        when(userRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(recipient, secondRecipient));

        notificationWriter.createAll(
                List.of(1L, 1L, 2L),
                NotificationType.MEETING_POST_CREATED,
                "[모임명]에 작성자님이 새 게시글을 등록했어요.",
                NotificationTargetType.POST,
                30L,
                40L);

        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("다건 알림 저장도 항상 새로운 트랜잭션에서 실행된다")
    void createAllUsesRequiresNewTransaction() throws NoSuchMethodException {
        Transactional transactional =
                NotificationWriter.class
                        .getMethod(
                                "createAll",
                                List.class,
                                NotificationType.class,
                                String.class,
                                NotificationTargetType.class,
                                Long.class,
                                Long.class)
                        .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
