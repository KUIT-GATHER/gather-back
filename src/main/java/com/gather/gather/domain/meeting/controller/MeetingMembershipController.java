package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.service.MeetingMembershipService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting Membership", description = "모임 멤버십(나가기 등) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/members")
public class MeetingMembershipController {

    private final MeetingMembershipService meetingMembershipService;

    @Operation(
            summary = "모임 나가기",
            description = "로그인한 팀원이 모임을 탈퇴합니다. 팀장(HOST)은 나갈 수 없습니다. 나가면 명단에서 제외되고 현재 인원이 1 감소합니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> leaveMeeting(@PathVariable Long meetingId) {
        meetingMembershipService.leaveMeeting(meetingId);
        return ApiResponse.success(null);
    }
}
