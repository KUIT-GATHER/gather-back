package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingMemberDetailResponse;
import com.gather.gather.domain.meeting.service.MeetingMemberManagementService;
import com.gather.gather.domain.meeting.service.MeetingMembershipService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting Membership", description = "모임 멤버십(나가기·팀장의 멤버 관리) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/members")
public class MeetingMembershipController {

    private final MeetingMembershipService meetingMembershipService;
    private final MeetingMemberManagementService meetingMemberManagementService;

    @Operation(
            summary = "모임 나가기",
            description = "로그인한 팀원이 모임을 탈퇴합니다. 팀장(HOST)은 나갈 수 없습니다. 나가면 명단에서 제외되고 현재 인원이 1 감소합니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> leaveMeeting(@PathVariable Long meetingId) {
        meetingMembershipService.leaveMeeting(meetingId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "모임원 상세 조회", description = "모임원의 연락처 등 상세 정보를 조회합니다(팀장 전용).")
    @GetMapping("/{userId}")
    public ApiResponse<MeetingMemberDetailResponse> getMemberDetail(
            @PathVariable Long meetingId, @PathVariable Long userId) {
        return ApiResponse.success(
                meetingMemberManagementService.getMemberDetail(meetingId, userId));
    }

    @Operation(
            summary = "모임원 내보내기",
            description =
                    "팀장이 모임원을 내보냅니다. 팀장 본인은 내보낼 수 없고, 아직 종료되지 않은 활동에 확정(CONFIRMED)된 참가자가 있으면"
                            + " 409로 거부됩니다. APPLIED 상태의 신청만 있다면 취소 처리 후 내보냅니다. 게시글·댓글·완료된 활동 기록은 유지됩니다.")
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long meetingId, @PathVariable Long userId) {
        meetingMemberManagementService.removeMember(meetingId, userId);
        return ApiResponse.success(null);
    }
}
