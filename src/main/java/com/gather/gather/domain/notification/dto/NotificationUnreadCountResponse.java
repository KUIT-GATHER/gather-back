package com.gather.gather.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "미읽음 알림 개수 응답")
public record NotificationUnreadCountResponse(
        @Schema(description = "활동 탭 미읽음 알림 개수", example = "2") long activity,
        @Schema(description = "모임 탭 미읽음 알림 개수", example = "3") long meeting,
        @Schema(description = "전체 미읽음 알림 개수", example = "5") long total) {

    public static NotificationUnreadCountResponse of(long activity, long meeting) {
        return new NotificationUnreadCountResponse(activity, meeting, activity + meeting);
    }
}
