package com.gather.gather.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
