package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "봉사공고 기반 모임 목록 응답")
public record PostingMeetingResponse(
        @Schema(description = "모임 ID", example = "12") Long meetingId,
        @Schema(description = "모임 이름", example = "한강공원 플로깅팀") String name,
        @Schema(description = "모임 카테고리", example = "ENVIRONMENT") PostingCategory category,
        @Schema(description = "현재 모임 인원", example = "12") Integer currentMemberCount,
        @Schema(description = "최대 모임 인원", example = "20") Integer maxMember,
        @Schema(description = "모임 상태", example = "RECRUITING") MeetingStatus status) {

    public static PostingMeetingResponse from(Meeting meeting, MeetingStatus displayStatus) {
        return new PostingMeetingResponse(
                meeting.getId(),
                meeting.getName(),
                meeting.getCategory(),
                meeting.getCurrentMemberCount(),
                meeting.getMaxMember(),
                displayStatus);
    }
}
