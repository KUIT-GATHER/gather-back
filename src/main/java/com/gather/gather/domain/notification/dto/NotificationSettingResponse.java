package com.gather.gather.domain.notification.dto;

import com.gather.gather.domain.notification.entity.NotificationSetting;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 응답")
public record NotificationSettingResponse(
        @Schema(description = "봉사 일정 알림", example = "true") boolean volunteerScheduleEnabled,
        @Schema(description = "북마크한 공고 모집 마감 임박 알림", example = "false")
                boolean bookmarkedPostingDeadlineEnabled,
        @Schema(description = "활동 뱃지 획득 알림", example = "false") boolean badgeEnabled,
        @Schema(description = "활동 작성 글 댓글 알림", example = "false")
                boolean activityPostCommentEnabled,
        @Schema(description = "모임 가입 승인·거절 알림", example = "true") boolean meetingJoinResultEnabled,
        @Schema(description = "북마크한 모임 모집 마감 임박 알림", example = "false")
                boolean bookmarkedMeetingDeadlineEnabled,
        @Schema(description = "모임 작성 글 댓글 알림", example = "false")
                boolean meetingPostCommentEnabled) {

    public static NotificationSettingResponse from(NotificationSetting setting) {

        return new NotificationSettingResponse(
                setting.isVolunteerScheduleEnabled(),
                setting.isBookmarkedPostingDeadlineEnabled(),
                setting.isBadgeEnabled(),
                setting.isActivityPostCommentEnabled(),
                setting.isMeetingJoinResultEnabled(),
                setting.isBookmarkedMeetingDeadlineEnabled(),
                setting.isMeetingPostCommentEnabled());
    }
}
