package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingCreateRequest;
import com.gather.gather.domain.meeting.dto.MeetingDetailResponse;
import com.gather.gather.domain.meeting.dto.MeetingJoinRequestResponse;
import com.gather.gather.domain.meeting.dto.MeetingJoinResponse;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.service.MeetingKeywordRecommendationService;
import com.gather.gather.domain.meeting.service.MeetingService;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting", description = "모임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingKeywordRecommendationService meetingKeywordRecommendationService;

    @Operation(summary = "모임 생성", description = "로그인한 사용자가 새로운 모임을 생성합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MeetingResponse> createMeeting(
            @Valid @RequestBody MeetingCreateRequest request) {
        return ApiResponse.success(meetingService.createMeeting(request));
    }

    @Operation(
            summary = "모임 목록 조회",
            description =
                    "모임을 페이지 단위로 조회합니다. 인증이 필요 없습니다. "
                            + "keyword, regionId, category, status 조건으로 필터링할 수 있습니다.",
            parameters = {
                @Parameter(
                        name = "sort",
                        description =
                                "정렬 기준 (property,direction). 예: createdAt,desc. "
                                        + "허용 필드: id, name, currentMemberCount, maxMember, "
                                        + "regionId, category, status, deadline, activityStartAt, "
                                        + "activityEndAt, createdAt, updatedAt.",
                        example = "createdAt,desc")
            })
    @GetMapping
    public ApiResponse<PageResponse<MeetingResponse>> getMeetings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId,
            @Parameter(description = "카테고리", example = "WELFARE") @RequestParam(required = false)
                    PostingCategory category,
            @RequestParam(required = false) MeetingStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.success(
                meetingService.getMeetings(keyword, regionId, category, status, pageable));
    }

    @Operation(
            summary = "모임 추천검색어 목록 조회",
            description =
                    "최근 60일간 모임 검색어를 형태소 분석해 집계한 인기 검색어 상위 10개를 반환합니다. "
                            + "매일 새벽 5시 배치로 갱신되며, 실시간 반영은 아닙니다. 인증이 필요 없습니다.")
    @GetMapping("/keywords/recommended")
    public ApiResponse<List<String>> getRecommendedKeywords() {
        return ApiResponse.success(meetingKeywordRecommendationService.getRecommendedKeywords());
    }

    @Operation(summary = "내 모임 조회", description = "로그인한 사용자가 참여한 모임 목록을 조회합니다.")
    @GetMapping("/my")
    public ApiResponse<List<MeetingResponse>> getMyMeetings() {
        return ApiResponse.success(meetingService.getMyMeetings());
    }

    @Operation(summary = "모임 가입 신청", description = "로그인한 사용자가 특정 모임에 가입을 신청합니다.")
    @PostMapping("/{meetingId}/join")
    public ApiResponse<MeetingJoinResponse> joinMeeting(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.joinMeeting(meetingId));
    }

    @Operation(summary = "가입 신청 목록 조회", description = "모임장이 승인 대기 중인 가입 신청 목록을 조회합니다.")
    @GetMapping("/{meetingId}/join-requests")
    public ApiResponse<List<MeetingJoinRequestResponse>> getPendingJoinRequests(
            @PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.getPendingJoinRequests(meetingId));
    }

    @Operation(summary = "모임 가입 승인", description = "모임장이 가입 신청을 승인합니다.")
    @PatchMapping("/{meetingId}/join-requests/{joinRequestId}/approve")
    public ApiResponse<MeetingJoinRequestResponse> approveJoinRequest(
            @PathVariable Long meetingId, @PathVariable Long joinRequestId) {
        return ApiResponse.success(meetingService.approveJoinRequest(meetingId, joinRequestId));
    }

    @Operation(summary = "모임 가입 거절", description = "모임장이 가입 신청을 거절합니다.")
    @PatchMapping("/{meetingId}/join-requests/{joinRequestId}/reject")
    public ApiResponse<MeetingJoinRequestResponse> rejectJoinRequest(
            @PathVariable Long meetingId, @PathVariable Long joinRequestId) {
        return ApiResponse.success(meetingService.rejectJoinRequest(meetingId, joinRequestId));
    }

    @Operation(summary = "모임 상세 조회", description = "meetingId에 해당하는 모임 상세 정보를 조회합니다. 인증이 필요 없습니다.")
    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingDetailResponse> getMeeting(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.getMeeting(meetingId));
    }
}
