package com.gather.gather.domain.badge.dto;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.entity.UserBadge;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "활동 뱃지 화면 - 뱃지 1종의 현재 상태(잠긴 뱃지 포함)")
public record BadgeStatusResponse(
        @Schema(description = "뱃지 종류") BadgeType badgeType,
        @Schema(description = "뱃지 이름") String title,
        @Schema(description = "뱃지 설명") String description,
        @Schema(description = "획득 여부") boolean earned,
        @Schema(description = "획득 일시(미획득 시 null)", nullable = true) LocalDateTime earnedAt,
        @Schema(description = "현재 진행 수치", example = "3") int currentValue,
        @Schema(description = "달성 기준", example = "5") int targetValue) {

    public static BadgeStatusResponse earned(UserBadge userBadge) {
        BadgeType badgeType = userBadge.getBadgeType();
        return new BadgeStatusResponse(
                badgeType,
                badgeType.getTitle(),
                badgeType.getDescription(),
                true,
                userBadge.getEarnedAt(),
                badgeType.getTargetValue(),
                badgeType.getTargetValue());
    }

    public static BadgeStatusResponse locked(BadgeType badgeType, int currentValue) {
        return new BadgeStatusResponse(
                badgeType,
                badgeType.getTitle(),
                badgeType.getDescription(),
                false,
                null,
                currentValue,
                badgeType.getTargetValue());
    }
}
