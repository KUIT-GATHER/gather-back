package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.badge.entity.Badge;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "활동 뱃지 화면 - 뱃지 카드")
public record BadgeCardResponse(
        @Schema(description = "뱃지 ID", example = "1") Long badgeId,
        @Schema(description = "뱃지 이름", example = "첫 봉사활동 완료") String name,
        @Schema(description = "뱃지 설명", example = "첫 봉사활동을 완료했어요") String description,
        @Schema(description = "달성 목표", example = "봉사활동 1회 완료") String targetDescription,
        @Schema(description = "뱃지 이미지 URL(에셋 미확보 시 null)", nullable = true) String imageUrl,
        @Schema(description = "달성 일시(미획득이면 null)", nullable = true) LocalDateTime achievedAt) {

    public static BadgeCardResponse of(Badge badge, LocalDateTime achievedAt) {
        return new BadgeCardResponse(
                badge.getId(),
                badge.getName(),
                badge.getDescription(),
                badge.getTargetDescription(),
                badge.getImageUrl(),
                achievedAt);
    }
}
