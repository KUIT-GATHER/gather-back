package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 신청자 목록 조회 응답(#13, 팀장 전용, 페이지네이션 없음). */
public record RecruitParticipantListResponse(
        Long postId,
        RecruitConfirmationStatus confirmationStatus,
        LocalDateTime confirmedAt,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt,
        List<RecruitParticipantSummaryItem> participants) {}
