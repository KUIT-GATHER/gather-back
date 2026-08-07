package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** 참여신청 토글 결과. */
public record RecruitParticipationResponse(
        @Schema(description = "참여신청 ID") Long participationId,
        MeetingRecruitParticipationStatus participationStatus,
        RecruitParticipationAction participationAction,
        @Schema(description = "현재 신청 인원") int appliedCount) {}
