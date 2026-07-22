package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "마이페이지 활동 캘린더 일정카드")
public record MyPageActivityResponse(
        @Schema(description = "참여(신청) ID", example = "1") Long participationId,
        @Schema(description = "봉사공고 ID (상세보기 이동에 사용)", example = "10") Long postingId,
        @Schema(description = "봉사공고 제목", example = "함께하는 환경정화 봉사") String title,
        @Schema(description = "활동 시작일", example = "2026-07-15") LocalDate actStartDate,
        @Schema(description = "활동 종료일", nullable = true, example = "2026-07-15")
                LocalDate actEndDate,
        @Schema(description = "활동 시작 시각", nullable = true, example = "09:00") String actStartTime,
        @Schema(description = "활동 종료 시각", nullable = true, example = "12:00") String actEndTime,
        @Schema(description = "활동 장소", nullable = true, example = "서울숲공원") String actPlace,
        @Schema(description = "참여 상태", example = "APPLIED") PostingParticipationStatus status) {

    public static MyPageActivityResponse of(PostingParticipation participation, Posting posting) {
        return new MyPageActivityResponse(
                participation.getId(),
                posting.getId(),
                posting.getTitle(),
                posting.getActStartDate(),
                posting.getActEndDate(),
                posting.getActStartTime(),
                posting.getActEndTime(),
                posting.getActPlace(),
                participation.getStatus());
    }
}
