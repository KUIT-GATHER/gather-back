package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.notification.dto.NotificationSettingResponse;
import com.gather.gather.domain.notification.dto.NotificationSettingUpdateRequest;
import com.gather.gather.domain.notification.entity.NotificationSetting;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;

    @Transactional
    public NotificationSettingResponse getSettings() {
        Long userId = SecurityUtil.getCurrentUserId();

        NotificationSetting setting =
                notificationSettingRepository
                        .findByUser_Id(userId)
                        .orElseGet(() -> createDefaultSetting(userId));

        return NotificationSettingResponse.from(setting);
    }

    @Transactional
    public NotificationSettingResponse updateSettings(NotificationSettingUpdateRequest request) {

        Long userId = SecurityUtil.getCurrentUserId();

        NotificationSetting setting =
                notificationSettingRepository
                        .findByUser_Id(userId)
                        .orElseGet(() -> createDefaultSetting(userId));

        setting.update(
                request.volunteerScheduleEnabled(),
                request.bookmarkedPostingDeadlineEnabled(),
                request.badgeEnabled(),
                request.activityPostCommentEnabled(),
                request.meetingJoinResultEnabled(),
                request.bookmarkedMeetingDeadlineEnabled(),
                request.meetingPostCommentEnabled());

        return NotificationSettingResponse.from(setting);
    }

    @Transactional
    public boolean isMeetingJoinResultEnabled(Long userId) {
        NotificationSetting setting =
                notificationSettingRepository
                        .findByUser_Id(userId)
                        .orElseGet(() -> createDefaultSetting(userId));

        return setting.isMeetingJoinResultEnabled();
    }

    private NotificationSetting createDefaultSetting(Long userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        NotificationSetting setting = NotificationSetting.createDefault(user);

        return notificationSettingRepository.save(setting);
    }
}
