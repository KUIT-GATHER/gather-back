package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDateTime;
import java.util.Set;

public record MeetingResponse(
        Long meetingId,
        String name,
        String description,
        Integer currentMemberCount,
        Integer maxMember,
        Long regionId,
        Set<PostingCategory> categories,
        MeetingStatus status,
        LocalDateTime deadline,
        LocalDateTime activityStartAt,
        MeetingMemberRole viewerRole) {

    // 목록/검색/생성 등 역할 정보가 없는 응답용(viewerRole = null).
    public static MeetingResponse from(Meeting meeting, MeetingStatus displayStatus) {
        return from(meeting, displayStatus, null);
    }

    // 내 모임 목록처럼 조회자의 역할이 있는 응답용.
    public static MeetingResponse from(
            Meeting meeting, MeetingStatus displayStatus, MeetingMemberRole viewerRole) {
        return new MeetingResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getDescription(),
                meeting.getCurrentMemberCount(),
                meeting.getMaxMember(),
                meeting.getRegionId(),
                Set.copyOf(meeting.getCategories()),
                displayStatus,
                meeting.getDeadline(),
                meeting.getActivityStartAt(),
                viewerRole);
    }
}
