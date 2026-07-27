package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "모임 가입 신청 결과")
public record MeetingJoinResponse(
        @Schema(description = "가입 신청 ID", example = "1") Long joinRequestId,
        @Schema(description = "모임 ID", example = "1") Long meetingId,
        @Schema(description = "가입 상태", example = "PENDING") MeetingMemberStatus status) {

    public static MeetingJoinResponse from(MeetingMember member) {
        return new MeetingJoinResponse(
                member.getId(), member.getMeeting().getId(), member.getStatus());
    }
}
