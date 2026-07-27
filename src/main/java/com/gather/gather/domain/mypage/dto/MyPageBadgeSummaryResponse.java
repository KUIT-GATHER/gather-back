package com.gather.gather.domain.mypage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "활동 뱃지 화면")
public record MyPageBadgeSummaryResponse(
        @Schema(description = "총 획득한 뱃지 수", example = "3") long earnedCount,
        @Schema(description = "전체 뱃지 수", example = "8") int totalCount,
        @Schema(description = "전체 달성률(0~1)", example = "0.375") double progressRate,
        @Schema(description = "뱃지 카드 목록(전체, 미획득 포함)") List<BadgeCardResponse> badges) {

    public static MyPageBadgeSummaryResponse of(
            long earnedCount, int totalCount, double progressRate, List<BadgeCardResponse> badges) {
        return new MyPageBadgeSummaryResponse(earnedCount, totalCount, progressRate, badges);
    }
}
