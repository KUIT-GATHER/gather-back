package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "활동기록 화면 - 활동 현황(총 완료 횟수 + 분야별 블럭 + 인정시간 집계)")
public record MyPageActivitySummaryResponse(
        @Schema(
                        description =
                                "총 활동 완료 횟수(봉사공고 참여 + 모임 봉사 합산). 모임은 다중 분야를 가질 수 있어 분야별 블럭에는 집계되지 않으므로,"
                                        + " categoryBlocks 합계보다 클 수 있다.",
                        example = "12")
                long totalCompletedCount,
        @Schema(description = "분야별 블럭(봉사공고 참여만 집계, 전체 분야, 미수행 분야는 count=0)")
                List<CategoryBlock> categoryBlocks,
        @Schema(description = "총 인정 활동 시간(분 단위, 봉사공고 참여 + 모임 봉사 합산)", example = "480")
                long totalRecognizedMinutes,
        @Schema(description = "인정시간이 입력된(시간 인증된) 완료 활동 횟수(봉사공고 참여 + 모임 봉사 합산)", example = "5")
                long timeCertifiableCompletedCount) {

    public static MyPageActivitySummaryResponse of(
            long totalCompletedCount,
            List<CategoryBlock> categoryBlocks,
            long totalRecognizedMinutes,
            long timeCertifiableCompletedCount) {
        return new MyPageActivitySummaryResponse(
                totalCompletedCount,
                categoryBlocks,
                totalRecognizedMinutes,
                timeCertifiableCompletedCount);
    }

    @Schema(description = "분야별 완료 횟수 블럭")
    public record CategoryBlock(
            @Schema(description = "봉사 분야") PostingCategory category,
            @Schema(description = "해당 분야 완료 횟수", example = "3") long count) {}
}
