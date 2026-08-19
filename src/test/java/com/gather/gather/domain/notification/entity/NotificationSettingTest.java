package com.gather.gather.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationSettingTest {

    @Test
    @DisplayName("기본 알림 설정을 생성한다")
    void createDefault_createsExpectedSettings() {
        User user = Mockito.mock(User.class);

        NotificationSetting setting = NotificationSetting.createDefault(user);

        assertThat(setting.getUser()).isSameAs(user);

        assertThat(setting.isVolunteerScheduleEnabled()).isTrue();
        assertThat(setting.isBookmarkedPostingDeadlineEnabled()).isTrue();
        assertThat(setting.isBadgeEnabled()).isTrue();
        assertThat(setting.isActivityPostCommentEnabled()).isTrue();

        assertThat(setting.isMeetingJoinResultEnabled()).isTrue();
        assertThat(setting.isBookmarkedMeetingDeadlineEnabled()).isTrue();
        assertThat(setting.isMeetingPostCommentEnabled()).isTrue();
    }

    @Test
    @DisplayName("알림 설정 전체를 변경한다")
    void update_updatesAllSettings() {
        NotificationSetting setting = NotificationSetting.createDefault(Mockito.mock(User.class));

        setting.update(false, true, true, true, false, true, true);

        assertThat(setting.isVolunteerScheduleEnabled()).isFalse();
        assertThat(setting.isBookmarkedPostingDeadlineEnabled()).isTrue();
        assertThat(setting.isBadgeEnabled()).isTrue();
        assertThat(setting.isActivityPostCommentEnabled()).isTrue();

        assertThat(setting.isMeetingJoinResultEnabled()).isFalse();
        assertThat(setting.isBookmarkedMeetingDeadlineEnabled()).isTrue();
        assertThat(setting.isMeetingPostCommentEnabled()).isTrue();
    }
}
