package com.gather.gather.domain.recruit.controller;

import com.gather.gather.domain.recruit.dto.AttendanceUpdateRequest;
import com.gather.gather.domain.recruit.dto.ConfirmRecruitParticipantsResponse;
import com.gather.gather.domain.recruit.dto.RecruitManageResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipantDetailResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipantListResponse;
import com.gather.gather.domain.recruit.dto.RejectParticipantResponse;
import com.gather.gather.domain.recruit.dto.UpdateAttendanceResponse;
import com.gather.gather.domain.recruit.service.MeetingRecruitManagementService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting Recruit Management", description = "팀장용 모집공고 관리·신청자 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/posts")
public class MeetingRecruitManagementController {

    private final MeetingRecruitManagementService meetingRecruitManagementService;

    @Operation(summary = "모집공고 관리 목록", description = "모임에서 작성한 모집공고 전체를 반환합니다(팀장 전용, 페이지네이션 없음).")
    @GetMapping("/recruits")
    public ApiResponse<List<RecruitManageResponse>> getManageList(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingRecruitManagementService.getManageList(meetingId));
    }

    @Operation(
            summary = "신청자 목록",
            description = "모집공고의 신청자 전체를 반환합니다(팀장 전용, 페이지네이션 없음). 개인정보는 포함하지 않습니다.")
    @GetMapping("/{postId}/recruit/participants")
    public ApiResponse<RecruitParticipantListResponse> getParticipants(
            @PathVariable Long meetingId, @PathVariable Long postId) {
        return ApiResponse.success(meetingRecruitManagementService.getParticipants(meetingId, postId));
    }

    @Operation(summary = "신청자 상세", description = "신청자 개인정보(연락처 등)를 포함한 상세 정보를 반환합니다(팀장 전용).")
    @GetMapping("/{postId}/recruit/participants/{participationId}")
    public ApiResponse<RecruitParticipantDetailResponse> getParticipantDetail(
            @PathVariable Long meetingId, @PathVariable Long postId, @PathVariable Long participationId) {
        return ApiResponse.success(
                meetingRecruitManagementService.getParticipantDetail(meetingId, postId, participationId));
    }

    @Operation(summary = "신청자 반려", description = "신청(APPLIED) 상태의 신청자를 반려(REJECTED)합니다(팀장 전용). 반려된 신청은 다시 신청할 수 없습니다.")
    @PatchMapping("/{postId}/recruit/participants/{participationId}/reject")
    public ApiResponse<RejectParticipantResponse> rejectParticipant(
            @PathVariable Long meetingId, @PathVariable Long postId, @PathVariable Long participationId) {
        return ApiResponse.success(
                meetingRecruitManagementService.rejectParticipant(meetingId, postId, participationId));
    }

    @Operation(
            summary = "현재 신청 인원 확정",
            description =
                    "현재 APPLIED 신청자 전체를 CONFIRMED로 일괄 확정합니다(팀장 전용). 신청자가 0명이면 확정할 수 없고, 한 번 확정한"
                            + " 뒤에는 취소할 수 없습니다.")
    @PatchMapping("/{postId}/recruit/participants/confirm")
    public ApiResponse<ConfirmRecruitParticipantsResponse> confirmParticipants(
            @PathVariable Long meetingId, @PathVariable Long postId) {
        return ApiResponse.success(meetingRecruitManagementService.confirmParticipants(meetingId, postId));
    }

    @Operation(
            summary = "출석 처리",
            description =
                    "참가 인원 확정 후, 활동 종료 시각이 지난 뒤에만 가능합니다(팀장 전용). PRESENT면 완료 처리 및 인정 시간 반영, ABSENT면"
                            + " 확정 상태 유지 및 인정 시간 차감. 동일 상태 재요청은 멱등하게 무시됩니다.")
    @PatchMapping("/{postId}/recruit/participants/{participationId}/attendance")
    public ApiResponse<UpdateAttendanceResponse> updateAttendance(
            @PathVariable Long meetingId,
            @PathVariable Long postId,
            @PathVariable Long participationId,
            @Valid @RequestBody AttendanceUpdateRequest request) {
        return ApiResponse.success(
                meetingRecruitManagementService.updateAttendance(
                        meetingId, postId, participationId, request));
    }
}
