package com.gather.gather.domain.notification.dto;

import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "알림 응답")
public record NotificationResponse(
        @Schema(description = "알림 ID", example = "1") Long id,
        @Schema(description = "알림 카테고리", example = "MEETING") NotificationCategory category,
        @Schema(description = "알림 유형", example = "MEETING_JOIN_APPROVED") NotificationType type,
        @Schema(description = "알림 내용", example = "[한강공원 플로깅팀] 가입이 승인되었어요.") String message,
        @Schema(description = "이동 대상 유형", example = "MEETING") NotificationTargetType targetType,
        @Schema(description = "이동 대상 ID", example = "10", nullable = true) Long targetId,
        @Schema(
                        description = "게시글 이동에 필요한 모임 ID. targetType이 POST가 아니면 null입니다.",
                        example = "3",
                        nullable = true)
                Long targetMeetingId,
        @Schema(description = "읽음 여부", example = "false") boolean read,
        @Schema(description = "알림 생성 일시") LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getCategory(),
                notification.getType(),
                notification.getMessage(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getTargetMeetingId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
