package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 봉사 신청(참여 일정 등록) 응답.
 *
 * <p>기존에는 이 API가 외부 이동을 트리거하는 진입점이라 applicationUrl을 함께 내려줬지만, 신규 플로우에서는 외부 신청 페이지 오픈은 공고 상세
 * 조회({@code GET /api/v1/postings/{id}}) 응답의 applicationUrl로 이미 처리된 뒤이므로 이 응답에서는 더 이상 내려주지 않는다.
 */
@Schema(description = "봉사 신청(참여 일정 등록) 응답")
public record PostingParticipationResponse(
        Long participationId,
        PostingParticipationStatus status,
        @Schema(example = "2026-08-15") LocalDate participationStartDate,
        @Schema(example = "2026-08-18") LocalDate participationEndDate) {

    public static PostingParticipationResponse of(PostingParticipation participation) {
        return new PostingParticipationResponse(
                participation.getId(),
                participation.getStatus(),
                participation.getParticipationStartDate(),
                participation.getParticipationEndDate());
    }
}
