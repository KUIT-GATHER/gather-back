package com.gather.gather.domain.recruit.dto;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Set;

/** 모집공고 상세. 기본 게시글 정보 + 모집 확장 필드 + 참여 현황/상태를 함께 내려 프론트의 신청 폼을 그린다. */
public record RecruitDetailResponse(
        Long postId,
        Long meetingId,
        String meetingName,
        String title,
        String content,
        Long authorId,
        String authorNickname,
        Long regionId,
        String regionName,
        String place,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt,
        LocalDateTime applyDeadlineAt,
        Integer maxParticipants,
        @Schema(description = "현재 신청 인원(취소/반려 제외)") int appliedCount,
        Set<PostingCategory> categories,
        boolean timeRecognized,
        Integer recognizedMinutes,
        @Schema(description = "외부 공개 여부") boolean external,
        Integer likeCount,
        Integer commentCount,
        @Schema(description = "신청 가능 시각인지(마감 전)") boolean applicationOpen,
        @Schema(description = "정원이 찼는지") boolean full,
        RecruitConfirmationStatus confirmationStatus,
        LocalDateTime confirmedAt,
        @Schema(description = "조회자의 참여 상태. 참여 이력이 없으면 null")
                MeetingRecruitParticipationStatus participationStatus,
        RecruitParticipationAction participationAction,
        @Schema(description = "조회자가 이 글을 수정할 수 있는지(작성자 본인)") boolean canEdit,
        @Schema(description = "조회자가 이 글을 삭제할 수 있는지(작성자 본인 또는 모임장)") boolean canDelete,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
