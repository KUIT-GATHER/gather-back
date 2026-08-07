package com.gather.gather.domain.posting.repository;

import java.time.LocalDateTime;

/** #api/v1/postings 통합 목록의 원시 조회 행(POSTING/MEETING_RECRUIT 공통). categoriesJson은 JSON 배열 문자열(예: ["ENVIRONMENT"]). */
public record UnifiedPostingRow(
        String sourceType,
        Long id,
        Long meetingId,
        String title,
        String organizationName,
        Long regionId,
        String place,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt,
        LocalDateTime applyDeadlineAt,
        Integer maxParticipants,
        Integer appliedCount,
        String categoriesJson,
        String status) {}
