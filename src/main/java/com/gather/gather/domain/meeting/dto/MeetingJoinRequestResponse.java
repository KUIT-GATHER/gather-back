package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "모임 가입 신청자")
public record MeetingJoinRequestResponse(
        @Schema(description = "가입 신청 ID", example = "1") Long joinRequestId,
        @Schema(description = "신청자 ID", example = "10") Long userId,
        @Schema(description = "신청자 닉네임", example = "개더러") String nickname,
        @Schema(description = "가입 상태", example = "PENDING") MeetingMemberStatus status,
        @Schema(description = "신청 일시") LocalDateTime requestedAt) {

    public static MeetingJoinRequestResponse from(MeetingMember member) {
        return new MeetingJoinRequestResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getStatus(),
                member.getJoinedAt());
    }
}
