package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import java.time.LocalDateTime;

/** 팀장용 모집공고 관리 목록 조회용 프로젝션(JPQL 생성자 표현식). 순서·타입이 쿼리와 일치해야 한다. */
public record RecruitManageItem(
        Long postId,
        String title,
        String place,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt,
        LocalDateTime applyDeadlineAt,
        Long appliedCount,
        Integer maxParticipants,
        boolean external,
        RecruitConfirmationStatus confirmationStatus,
        LocalDateTime confirmedAt) {}
