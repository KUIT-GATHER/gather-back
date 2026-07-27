package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 활동기록 상세의 봉사 카드. 시간 인증(Nh/미인증) 필드는 산출 기준 미정으로 이번 스프린트 응답에서 제외한다(devplan2 8-2① 참고). 프론트는 해당 UI를 다음
 * 스프린트까지 숨기거나 항상 미인증으로 처리해야 한다.
 */
@Schema(description = "활동기록 상세 - 봉사 카드")
public record MyPageActivityRecordResponse(
        @Schema(description = "참여(신청) ID", example = "1") Long participationId,
        @Schema(description = "봉사공고 ID", example = "10") Long postingId,
        @Schema(description = "봉사공고 제목", example = "함께하는 환경정화 봉사") String title,
        @Schema(description = "봉사 분야(카드 테두리 색 결정에 사용)") PostingCategory category,
        @Schema(description = "활동 시작일", example = "2026-07-15") LocalDate actStartDate,
        @Schema(description = "활동 종료일", nullable = true, example = "2026-07-15")
                LocalDate actEndDate,
        @Schema(description = "활동 장소", nullable = true, example = "서울숲공원") String actPlace) {

    public static MyPageActivityRecordResponse of(
            PostingParticipation participation, Posting posting) {
        return new MyPageActivityRecordResponse(
                participation.getId(),
                posting.getId(),
                posting.getTitle(),
                posting.getCategory(),
                posting.getActStartDate(),
                posting.getActEndDate(),
                posting.getActPlace());
    }
}
