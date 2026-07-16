package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;

public record PostingSummaryResponse(
        Long id,
        String title,
        PostingStatus status,
        String recruitOrg,
        LocalDate actStartDate,
        LocalDate actEndDate,
        String actPlace,
        Integer recruitCount,
        Integer applicantCount,
        Long regionId,
        String regionName,
        PostingCategory category,
        LocalDate noticeEndDate) {

    public static PostingSummaryResponse from(Posting posting, String regionName) {
        return new PostingSummaryResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getStatus(),
                posting.getRecruitOrg(),
                posting.getActStartDate(),
                posting.getActEndDate(),
                posting.getActPlace(),
                posting.getRecruitCount(),
                posting.getApplicantCount(),
                posting.getRegionId(),
                regionName,
                posting.getCategory(),
                posting.getNoticeEndDate());
    }
}
