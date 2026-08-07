package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import java.time.LocalDateTime;

/** 팀장용 모집공고 관리 목록 항목(#12). */
public record RecruitManageResponse(
        Long postId,
        String title,
        String place,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt,
        LocalDateTime applyDeadlineAt,
        long appliedCount,
        int maxParticipants,
        boolean external,
        boolean applicationOpen,
        RecruitConfirmationStatus confirmationStatus,
        LocalDateTime confirmedAt,
        boolean canEdit) {}
