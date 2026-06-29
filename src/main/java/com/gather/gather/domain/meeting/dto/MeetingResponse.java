package com.gather.gather.domain.meeting.dto;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import java.time.LocalDateTime;
public record MeetingResponse(
        Long meetingId,
        String name,
        String description,
        Integer currentMemberCount,
        Integer maxMember,
        Long regionId,
        Long categoryId,
        MeetingStatus status,
        LocalDateTime deadline,
        LocalDateTime activityStartAt) {

    public static MeetingResponse from(Meeting meeting, MeetingStatus displayStatus) {
        return new MeetingResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getDescription(),
                meeting.getCurrentMemberCount(),
                meeting.getMaxMember(),
                meeting.getRegionId(),
                meeting.getCategoryId(),
                displayStatus,
                meeting.getDeadline(),
                meeting.getActivityStartAt());
    }
}