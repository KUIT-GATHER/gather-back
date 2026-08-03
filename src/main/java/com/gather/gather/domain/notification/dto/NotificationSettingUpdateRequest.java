package com.gather.gather.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 설정 변경 요청")
public record NotificationSettingUpdateRequest(
        @Schema(description = "봉사 일정 알림", example = "true")
                @NotNull(message = "봉사 일정 알림 설정은 필수입니다.")
                Boolean volunteerScheduleEnabled,
        @Schema(description = "북마크한 공고 모집 마감 임박 알림", example = "false")
                @NotNull(message = "공고 모집 마감 알림 설정은 필수입니다.")
                Boolean bookmarkedPostingDeadlineEnabled,
        @Schema(description = "활동 뱃지 획득 알림", example = "false")
                @NotNull(message = "뱃지 획득 알림 설정은 필수입니다.")
                Boolean badgeEnabled,
        @Schema(description = "활동 작성 글 댓글 알림", example = "false")
                @NotNull(message = "활동 댓글 알림 설정은 필수입니다.")
                Boolean activityPostCommentEnabled,
        @Schema(description = "모임 가입 승인·거절 알림", example = "true")
                @NotNull(message = "모임 가입 결과 알림 설정은 필수입니다.")
                Boolean meetingJoinResultEnabled,
        @Schema(description = "북마크한 모임 모집 마감 임박 알림", example = "false")
                @NotNull(message = "모임 모집 마감 알림 설정은 필수입니다.")
                Boolean bookmarkedMeetingDeadlineEnabled,
        @Schema(description = "모임 작성 글 댓글 알림", example = "false")
                @NotNull(message = "모임 댓글 알림 설정은 필수입니다.")
                Boolean meetingPostCommentEnabled) {}
