package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import java.time.LocalDateTime;

/** 신청 인원 확정 응답(#13). */
public record ConfirmRecruitParticipantsResponse(
        Long postId, RecruitConfirmationStatus confirmationStatus, LocalDateTime confirmedAt, int confirmedCount) {}
