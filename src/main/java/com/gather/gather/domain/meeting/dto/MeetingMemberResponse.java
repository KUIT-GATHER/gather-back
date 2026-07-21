package com.gather.gather.domain.meeting.dto;

import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;

/** 모임 홈 팀원 목록 항목. 팀장(HOST)이 목록 첫 번째에 오도록 서비스에서 정렬한다. */
public record MeetingMemberResponse(
        Long userId, String nickname, MeetingMemberRole role, boolean host) {

    public static MeetingMemberResponse from(MeetingMember member) {
        return new MeetingMemberResponse(
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getRole(),
                member.getRole() == MeetingMemberRole.HOST);
    }
}
