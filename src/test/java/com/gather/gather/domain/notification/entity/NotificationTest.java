package com.gather.gather.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationTest {

    @Test
    @DisplayName("알림 유형에 따라 카테고리가 결정된다")
    void create_derivesCategoryFromType() {
        User recipient = org.mockito.Mockito.mock(User.class);

        Notification notification =
                Notification.create(
                        recipient,
                        NotificationType.MEETING_JOIN_APPROVED,
                        "[모임명] 가입이 승인되었어요.",
                        NotificationTargetType.MEETING,
                        1L);

        assertThat(notification.getCategory()).isEqualTo(NotificationCategory.MEETING);
        assertThat(notification.isRead()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    @DisplayName("모든 알림 유형은 이름 규칙에 맞는 카테고리를 가진다")
    void notificationType_hasExpectedCategory(NotificationType type) {
        NotificationCategory expectedCategory =
                type.name().startsWith("MEETING_")
                        ? NotificationCategory.MEETING
                        : NotificationCategory.ACTIVITY;

        assertThat(type.getCategory()).isEqualTo(expectedCategory);
    }

    @Test
    @DisplayName("알림 읽음 처리는 여러 번 호출해도 최초 시간만 유지한다")
    void markAsRead_isIdempotent() {
        Notification notification =
                Notification.create(
                        org.mockito.Mockito.mock(User.class),
                        NotificationType.BADGE_EARNED,
                        "새로운 뱃지를 획득했어요.",
                        NotificationTargetType.MY_PAGE,
                        null);

        notification.markAsRead();
        var firstReadAt = notification.getReadAt();
        notification.markAsRead();

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("알림 삭제는 여러 번 호출해도 최초 삭제 시간만 유지한다")
    void delete_isIdempotent() {
        Notification notification = createMyPageNotification();

        notification.delete();
        var firstDeletedAt = notification.getDeletedAt();
        notification.delete();

        assertThat(notification.getDeletedAt()).isEqualTo(firstDeletedAt);
    }

    @ParameterizedTest
    @EnumSource(
            value = NotificationTargetType.class,
            names = "MY_PAGE",
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("화면 이동에 ID가 필요한 대상은 targetId 없이 생성할 수 없다")
    void create_rejectsMissingTargetId(NotificationTargetType targetType) {
        assertThatThrownBy(
                        () ->
                                Notification.create(
                                        org.mockito.Mockito.mock(User.class),
                                        NotificationType.MEETING_POST_CREATED,
                                        "새로운 알림입니다.",
                                        targetType,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이동 대상 ID가 필요한 알림입니다.");
    }

    @Test
    @DisplayName("마이페이지 이동 알림은 targetId 없이 생성할 수 있다")
    void create_allowsMyPageWithoutTargetId() {
        Notification notification = createMyPageNotification();

        assertThat(notification.getTargetType()).isEqualTo(NotificationTargetType.MY_PAGE);
        assertThat(notification.getTargetId()).isNull();
    }

    private Notification createMyPageNotification() {
        return Notification.create(
                org.mockito.Mockito.mock(User.class),
                NotificationType.BADGE_EARNED,
                "새로운 뱃지를 획득했어요.",
                NotificationTargetType.MY_PAGE,
                null);
    }
}
