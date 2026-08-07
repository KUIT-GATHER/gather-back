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
        String regionName,
        Set<PostingCategory> categories,
        MeetingStatus status,
        LocalDateTime deadline,
        LocalDateTime activityStartAt,
        MeetingMemberRole viewerRole,
        /** 완료 처리된 모임에서 조회자 본인이 입력한 봉사 인정시간(분 단위). 미입력이거나 조회자 역할 정보가 없으면 null. */
        Integer recognizedMinutes) {

    // 목록/검색/생성 등 역할 정보가 없는 응답용(viewerRole = null).
    public static MeetingResponse from(
            Meeting meeting, MeetingStatus displayStatus, String regionName) {
        return from(meeting, displayStatus, regionName, null, null);
    }

    // 내 모임 목록처럼 조회자의 역할만 있고 인정시간은 없는 응답용.
    public static MeetingResponse from(
            Meeting meeting,
            MeetingStatus displayStatus,
            String regionName,
            MeetingMemberRole viewerRole) {
        return from(meeting, displayStatus, regionName, viewerRole, null);
    }

    // 내 모임 목록 - 조회자의 역할과 본인이 입력한 인정시간을 함께 담는 응답용.
    public static MeetingResponse from(
            Meeting meeting,
            MeetingStatus displayStatus,
            String regionName,
            MeetingMemberRole viewerRole,
            Integer recognizedMinutes) {
        return new MeetingResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getDescription(),
                meeting.getCurrentMemberCount(),
                meeting.getMaxMember(),
                meeting.getRegionId(),
                regionName,
                Set.copyOf(meeting.getCategories()),
                displayStatus,
                meeting.getDeadline(),
                meeting.getActivityStartAt(),
                viewerRole,
                recognizedMinutes);
    }
}
