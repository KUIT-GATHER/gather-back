package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "활동기록 상세 - 봉사 카드")
public record MyPageActivityRecordResponse(
        @Schema(description = "참여(신청) ID", example = "1") Long participationId,
        @Schema(description = "봉사공고 ID", example = "10") Long postingId,
        @Schema(description = "봉사공고 제목", example = "함께하는 환경정화 봉사") String title,
        @Schema(description = "봉사 분야(카드 테두리 색 결정에 사용)") PostingCategory category,
        @Schema(description = "활동 시작일", example = "2026-07-15") LocalDate actStartDate,
        @Schema(description = "활동 종료일", nullable = true, example = "2026-07-15")
                LocalDate actEndDate,
        @Schema(description = "활동 장소", nullable = true, example = "서울숲공원") String actPlace,
        @Schema(description = "인정시간(분 단위, 미입력 시 null)", nullable = true, example = "120")
                Integer recognizedMinutes) {

    public static MyPageActivityRecordResponse of(
            PostingParticipation participation, Posting posting) {
        return new MyPageActivityRecordResponse(
                participation.getId(),
                posting.getId(),
                posting.getTitle(),
                posting.getCategory(),
                posting.getActStartDate(),
                posting.getActEndDate(),
                posting.getActPlace(),
                participation.getRecognizedMinutes());
    }
}
