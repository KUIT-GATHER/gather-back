package com.gather.gather.domain.posting.dto;

import com.gather.gather.domain.posting.entity.Posting;
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
        Long categoryId,
        String categoryName) {

    public static PostingSummaryResponse from(
            Posting posting, String regionName, String categoryName) {
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
                posting.getCategoryId(),
                categoryName);
    }
}
