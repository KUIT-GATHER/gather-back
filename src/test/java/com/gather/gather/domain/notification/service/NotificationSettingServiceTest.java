package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.notification.dto.NotificationSettingResponse;
import com.gather.gather.domain.notification.dto.NotificationSettingUpdateRequest;
import com.gather.gather.domain.notification.entity.NotificationSetting;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private NotificationSettingRepository notificationSettingRepository;

    @Mock private UserRepository userRepository;

    @Mock private User user;

    @InjectMocks private NotificationSettingService notificationSettingService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("저장된 알림 설정을 조회한다")
    void getSettings_returnsExistingSetting() {
        NotificationSetting setting = NotificationSetting.createDefault(user);

        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(setting));

        NotificationSettingResponse response = notificationSettingService.getSettings();

        assertThat(response.volunteerScheduleEnabled()).isTrue();
        assertThat(response.bookmarkedPostingDeadlineEnabled()).isTrue();
        assertThat(response.badgeEnabled()).isTrue();
        assertThat(response.activityPostCommentEnabled()).isTrue();
        assertThat(response.meetingJoinResultEnabled()).isTrue();
        assertThat(response.bookmarkedMeetingDeadlineEnabled()).isTrue();
        assertThat(response.meetingPostCommentEnabled()).isTrue();
    }

    @Test
    @DisplayName("설정이 없으면 기본 설정을 생성한다")
    void getSettings_createsDefaultSetting() {
        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(notificationSettingRepository.save(
                        org.mockito.ArgumentMatchers.any(NotificationSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationSettingResponse response = notificationSettingService.getSettings();

        assertThat(response.volunteerScheduleEnabled()).isTrue();
        assertThat(response.meetingJoinResultEnabled()).isTrue();

        verify(notificationSettingRepository)
                .save(org.mockito.ArgumentMatchers.any(NotificationSetting.class));
    }

    @Test
    @DisplayName("알림 설정 전체를 변경한다")
    void updateSettings_updatesAllSettings() {
        NotificationSetting setting = NotificationSetting.createDefault(user);

        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(setting));

        NotificationSettingUpdateRequest request =
                new NotificationSettingUpdateRequest(false, true, true, true, false, true, true);

        NotificationSettingResponse response = notificationSettingService.updateSettings(request);

        assertThat(response.volunteerScheduleEnabled()).isFalse();
        assertThat(response.bookmarkedPostingDeadlineEnabled()).isTrue();
        assertThat(response.badgeEnabled()).isTrue();
        assertThat(response.activityPostCommentEnabled()).isTrue();

        assertThat(response.meetingJoinResultEnabled()).isFalse();
        assertThat(response.bookmarkedMeetingDeadlineEnabled()).isTrue();
        assertThat(response.meetingPostCommentEnabled()).isTrue();
    }

    @Test
    @DisplayName("저장된 모임 가입 결과 알림 설정을 반환한다")
    void isMeetingJoinResultEnabledReturnsExistingSetting() {
        NotificationSetting setting = NotificationSetting.createDefault(user);
        setting.update(true, true, true, true, false, true, true);

        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(setting));

        boolean enabled = notificationSettingService.isMeetingJoinResultEnabled(USER_ID);

        assertThat(enabled).isFalse();
    }

    @Test
    @DisplayName("알림 설정이 없으면 기본 설정을 생성하고 모임 가입 결과 알림을 활성화한다")
    void isMeetingJoinResultEnabledCreatesDefaultSetting() {
        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(notificationSettingRepository.save(
                        org.mockito.ArgumentMatchers.any(NotificationSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean enabled = notificationSettingService.isMeetingJoinResultEnabled(USER_ID);

        assertThat(enabled).isTrue();
        verify(notificationSettingRepository)
                .save(org.mockito.ArgumentMatchers.any(NotificationSetting.class));
    }

    @Test
    @DisplayName("저장된 모임 댓글 알림 설정을 반환한다")
    void isMeetingPostCommentEnabledReturnsExistingSetting() {
        NotificationSetting setting = NotificationSetting.createDefault(user);
        setting.update(true, true, true, true, true, true, true);

        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.of(setting));

        boolean enabled = notificationSettingService.isMeetingPostCommentEnabled(USER_ID);

        assertThat(enabled).isTrue();
    }

    @Test
    @DisplayName("설정이 없으면 기본 설정에 따라 모임 댓글 알림을 활성화한다")
    void isMeetingPostCommentEnabledCreatesDefaultEnabledSetting() {
        when(notificationSettingRepository.findByUser_Id(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(notificationSettingRepository.save(
                        org.mockito.ArgumentMatchers.any(NotificationSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean enabled = notificationSettingService.isMeetingPostCommentEnabled(USER_ID);

        assertThat(enabled).isTrue();

        verify(notificationSettingRepository)
                .save(org.mockito.ArgumentMatchers.any(NotificationSetting.class));
    }
}
