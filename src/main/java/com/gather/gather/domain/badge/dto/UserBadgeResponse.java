package com.gather.gather.domain.badge.dto;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.entity.UserBadge;
import java.time.LocalDateTime;

public record UserBadgeResponse(
        BadgeType badgeType, String title, String description, LocalDateTime earnedAt) {

    public static UserBadgeResponse from(UserBadge userBadge) {
        return new UserBadgeResponse(
                userBadge.getBadgeType(),
                userBadge.getBadgeType().getTitle(),
                userBadge.getBadgeType().getDescription(),
                userBadge.getEarnedAt());
    }
}
