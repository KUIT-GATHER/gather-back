package com.gather.gather.domain.recruit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 참여신청 토글 결과. 프론트는 이 값으로 버튼 상태(참여신청/신청완료)와 현황(n/N)을 갱신한다. */
public record RecruitParticipationResponse(
        @Schema(description = "토글 후 조회자의 신청 여부", example = "true") boolean applied,
        @Schema(description = "현재 신청 인원", example = "3") int appliedCount,
        @Schema(description = "모집 정원", example = "4") int maxParticipants) {}
