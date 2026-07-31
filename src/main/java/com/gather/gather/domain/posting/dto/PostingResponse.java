package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PostingResponse(
        Long id,
        String title,
        PostingStatus status,
        @Schema(nullable = true, description = "봉사공고 본문. 활동 종료 후 보존기간(1개월)이 지나면 null일 수 있다")
                String content,
        String recruitOrg,
        String registerOrg,
        LocalDate actStartDate,
        LocalDate actEndDate,
        String actStartTime,
        String actEndTime,
        LocalDate noticeStartDate,
        LocalDate noticeEndDate,
        String actWkdy,
        Integer recruitCount,
        Integer applicantCount,
        Boolean isAdult,
        Boolean isTeen,
        Boolean isGroup,
        String actPlace,
        String managerName,
        String managerTel,
        String managerFax,
        String managerEmail,
        String managerAddress,
        Long regionId,
        String regionName,
        PostingCategory category,
        List<PostingLocationResponse> locations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean bookmarked,
        @Schema(nullable = true, description = "로그인 사용자의 참여 상태. 비로그인 또는 미참여 시 null")
                PostingParticipationStatus participationStatus,
        @Schema(description = "공고 상세 하단 버튼 액션. participationStatus가 null이어도 항상 반환된다")
                PostingParticipationAction participationAction) {

    public static PostingResponse from(
            Posting posting,
            String regionName,
            List<PostingLocationResponse> locations,
            boolean bookmarked,
            PostingParticipationStatus participationStatus) {
        return new PostingResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getStatus(),
                posting.getContent(),
                posting.getRecruitOrg(),
                posting.getRegisterOrg(),
                posting.getActStartDate(),
                posting.getActEndDate(),
                posting.getActStartTime(),
                posting.getActEndTime(),
                posting.getNoticeStartDate(),
                posting.getNoticeEndDate(),
                posting.getActWkdy(),
                posting.getRecruitCount(),
                posting.getApplicantCount(),
                posting.getIsAdult(),
                posting.getIsTeen(),
                posting.getIsGroup(),
                posting.getActPlace(),
                posting.getManagerName(),
                posting.getManagerTel(),
                posting.getManagerFax(),
                posting.getManagerEmail(),
                posting.getManagerAddress(),
                posting.getRegionId(),
                regionName,
                posting.getCategory(),
                locations,
                posting.getCreatedAt(),
                posting.getUpdatedAt(),
                bookmarked,
                participationStatus,
                PostingParticipationAction.from(participationStatus));
    }
}
