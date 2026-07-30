package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "활동기록 화면 - 활동 현황(총 완료 횟수 + 분야별 블럭)")
public record MyPageActivitySummaryResponse(
        @Schema(description = "총 활동 완료 횟수", example = "12") long totalCompletedCount,
        @Schema(description = "분야별 블럭(전체 분야, 미수행 분야는 count=0)")
                List<CategoryBlock> categoryBlocks) {

    public static MyPageActivitySummaryResponse of(
            long totalCompletedCount, List<CategoryBlock> categoryBlocks) {
        return new MyPageActivitySummaryResponse(totalCompletedCount, categoryBlocks);
    }

    @Schema(description = "분야별 완료 횟수 블럭")
    public record CategoryBlock(
            @Schema(description = "봉사 분야") PostingCategory category,
            @Schema(description = "해당 분야 완료 횟수", example = "3") long count) {}
}
