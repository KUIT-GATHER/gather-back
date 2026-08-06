package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

/**
 * 모집공고 상세. 기본 게시글 정보 + 모집 확장 필드 + 참여 현황/상태를 함께 내려 프론트의 신청 폼을 그린다.
 *
 * <p>{@code applicationOpen}(마감 전) · {@code full}(정원 초과) · {@code applied}(내 신청 여부)로 버튼 상태를 판단한다.
 */
public record RecruitDetailResponse(
        Long postId,
        Long meetingId,
        String title,
        String content,
        Long authorId,
        String authorNickname,
        String place,
        LocalDate actDate,
        LocalTime actStartTime,
        LocalTime actEndTime,
        Integer maxParticipants,
        Set<PostingCategory> categories,
        boolean timeRecognized,
        Integer recognizedMinutes,
        LocalDateTime applyDeadline,
        @Schema(description = "외부 공고 공개 여부") boolean external,
        @Schema(description = "참여 조건(선택)") String participationCondition,
        Integer likeCount,
        Integer commentCount,
        @Schema(description = "현재 신청 인원") int appliedCount,
        @Schema(description = "조회자의 신청 여부") boolean applied,
        @Schema(description = "신청 가능 기간인지(마감일 이전)") boolean applicationOpen,
        @Schema(description = "정원이 찼는지") boolean full,
        @Schema(description = "조회자가 이 글을 수정할 수 있는지(작성자 본인)") boolean canEdit,
        @Schema(description = "조회자가 이 글을 삭제할 수 있는지(작성자 본인 또는 모임장)") boolean canDelete,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
