package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingSource;
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
        @Schema(nullable = true, description = "비로그인 또는 미참여 시 null") PostingParticipationStatus participationStatus,
        @Schema(description = "공고 상세 하단 버튼 액션. participationStatus가 null이어도 항상 반환된다")
        PostingParticipationAction participationAction,
        @Schema(description = "공고 원본 출처(API_1365: 1365자원봉사포털, VMS_CRAWL: VMS 크롤링)")
        PostingSource source,
        @Schema(
                nullable = true,
                description =
                        "외부(1365/VMS) 신청 페이지 URL. source에 따라 자동 분기되며 프론트는 이 값을 그대로 새 탭으로 열면 된다."
                                + " extId가 없는 등 링크를 만들 수 없는 공고는 null이며, 이 경우 신청하기 버튼을 비활성화하거나"
                                + " 안내 문구를 노출해야 한다.")
        String applicationUrl,
        @Schema(nullable = true, description = "로그인 사용자의 현재 참여 일정 시작일. 참여 이력이 없으면 null")
        LocalDate participationStartDate,
        @Schema(nullable = true, description = "로그인 사용자의 현재 참여 일정 종료일. 참여 이력이 없으면 null")
        LocalDate participationEndDate) {

    public static PostingResponse from(
            Posting posting,
            String regionName,
            List<PostingLocationResponse> locations,
            boolean bookmarked,
            PostingParticipationStatus participationStatus,
            String applicationUrl,
            LocalDate participationStartDate,
            LocalDate participationEndDate) {
        // 완료 가능 여부(activityEnded)는 개인 참여일정 종료일 기준으로 판단하고, 개인 일정이 없는(기존) 참여만
        // 공고 전체 활동종료일로 fallback한다. 실제 계산 로직은 PostingParticipationAction에서 공유한다.
        boolean activityEnded =
                PostingParticipationAction.resolveActivityEnded(
                        posting, participationEndDate, LocalDate.now());
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
                PostingParticipationAction.from(participationStatus, activityEnded),
                posting.getSource(),
                applicationUrl,
                participationStartDate,
                participationEndDate);
    }
}
