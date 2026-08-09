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
        Integer recognizedMinutes,
        /** 모임에 등록된 이미지 중 sortOrder가 가장 앞선 대표 이미지 URL. 등록된 이미지가 없으면 null(프론트에서 기본 이미지 표시). */
        String thumbnailUrl) {

    // 모임 생성 등 역할·썸네일 정보가 없는 응답용(viewerRole = null, thumbnailUrl = null).
    public static MeetingResponse from(
            Meeting meeting, MeetingStatus displayStatus, String regionName) {
        return from(meeting, displayStatus, regionName, null, null, null);
    }

    // 목록 응답용 - 배치 조회한 대표 이미지 URL을 포함하고 조회자 역할 정보는 없음(viewerRole = null).
    public static MeetingResponse from(
            Meeting meeting, MeetingStatus displayStatus, String regionName, String thumbnailUrl) {
        return from(meeting, displayStatus, regionName, null, null, thumbnailUrl);
    }

    // 내 모임 목록처럼 조회자의 역할만 있고 인정시간·썸네일은 없는 응답용.
    public static MeetingResponse from(
            Meeting meeting,
            MeetingStatus displayStatus,
            String regionName,
            MeetingMemberRole viewerRole) {
        return from(meeting, displayStatus, regionName, viewerRole, null, null);
    }

    // 내 모임 목록 - 조회자의 역할, 본인이 입력한 인정시간, 대표 이미지 URL을 함께 담는 응답용.
    public static MeetingResponse from(
            Meeting meeting,
            MeetingStatus displayStatus,
            String regionName,
            MeetingMemberRole viewerRole,
            Integer recognizedMinutes,
            String thumbnailUrl) {
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
                recognizedMinutes,
                thumbnailUrl);
    }
}
