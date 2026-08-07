package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 앱 전체 봉사공고 통합 목록(#9) 항목. {@code sourceType}에 따라 이동 대상이 다르다.
 *
 * <ul>
 *   <li>{@code POSTING}: {@code id}는 기존 봉사공고 ID, {@code meetingId}는 항상 null. 기존 봉사공고 상세로 이동.
 *   <li>{@code MEETING_RECRUIT}: {@code id}는 모집공고 postId, {@code meetingId}가 함께 내려온다. 실제 모임 봉사
 *       모집공고 상세({@code GET /meetings/{meetingId}/posts/{id}/recruit})로 이동한다(모임 게시판 상세로 이동하지 않음).
 * </ul>
 */
public record PostingListItem(
        PostingSourceType sourceType,
        Long id,
        Long meetingId,
        String title,
        @Schema(description = "MEETING_RECRUIT면 모임 이름, POSTING이면 모집기관명") String organizationName,
        @Schema(description = "MEETING_RECRUIT면 모임 대표 이미지(없으면 null), POSTING이면 항상 null") String thumbnailUrl,
        Long regionId,
        String regionName,
        String place,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt,
        LocalDateTime applyDeadlineAt,
        Integer maxParticipants,
        Integer appliedCount,
        List<PostingCategory> categories,
        String status) {}
