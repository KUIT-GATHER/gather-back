package com.gather.gather.domain.meeting.dto;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import java.time.LocalDateTime;
public record MeetingDetailResponse(
        Long meetingId,
        String name,
        String description,
        Integer currentMemberCount,
        Integer maxMember,
        Long regionId,
        Long categoryId,
        Long hostId,
        Long volunteerPostingId,
        String participationCondition,
        String memo,
        MeetingStatus status,
        LocalDateTime deadline,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt) {

    public static MeetingDetailResponse from(Meeting meeting, MeetingStatus displayStatus) {
        return new MeetingDetailResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getDescription(),
                meeting.getCurrentMemberCount(),
                meeting.getMaxMember(),
                meeting.getRegionId(),
                meeting.getCategoryId(),
                meeting.getHost().getId(),
                meeting.getVolunteerPostingId(),
                meeting.getParticipationCondition(),
                meeting.getMemo(),
                displayStatus,
                meeting.getDeadline(),
                meeting.getActivityStartAt(),
                meeting.getActivityEndAt());
    }
}