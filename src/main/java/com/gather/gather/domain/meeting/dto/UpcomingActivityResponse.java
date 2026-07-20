package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingStatus;
import java.time.LocalDate;

/**
 * 모임에 연관된 봉사공고를 "다가오는 활동" 카드로 노출하기 위한 응답.
 *
 * <p>{@code remainingCount} = 모집인원 - 신청인원 (음수 방지, 값이 없으면 null).
 */
public record UpcomingActivityResponse(
        Long postingId,
        String title,
        LocalDate activityDate,
        String startTime,
        String endTime,
        String place,
        Integer remainingCount,
        PostingStatus status) {

    public static UpcomingActivityResponse from(Posting posting) {
        return new UpcomingActivityResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getActivityDate(),
                posting.getActStartTime(),
                posting.getActEndTime(),
                posting.getActPlace(),
                resolveRemaining(posting.getRecruitCount(), posting.getApplicantCount()),
                posting.getStatus());
    }

    private static Integer resolveRemaining(Integer recruitCount, Integer applicantCount) {
        if (recruitCount == null) {
            return null;
        }
        int applied = applicantCount == null ? 0 : applicantCount;
        return Math.max(0, recruitCount - applied);
    }
}
