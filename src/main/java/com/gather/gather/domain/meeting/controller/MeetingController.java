package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingCreateRequest;
import com.gather.gather.domain.meeting.dto.MeetingDetailResponse;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.service.MeetingService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Operation(summary = "모임 생성", description = "로그인한 사용자가 새로운 모임을 생성합니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MeetingResponse> createMeeting(
            @Valid @RequestBody MeetingCreateRequest request) {
        return ApiResponse.success(meetingService.createMeeting(request));
    }

    @Operation(summary = "모임 목록 조회", description = "키워드, 지역, 카테고리, 상태 조건으로 모임 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<MeetingResponse>> getMeetings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) MeetingStatus status) {
        return ApiResponse.success(
                meetingService.getMeetings(keyword, regionId, categoryId, status));
    }

    @Operation(summary = "내 모임 조회", description = "로그인한 사용자가 참여한 모임 목록을 조회합니다.")
    @GetMapping("/my")
    public ApiResponse<List<MeetingResponse>> getMyMeetings() {
        return ApiResponse.success(meetingService.getMyMeetings());
    }

    @Operation(summary = "모임 참여", description = "로그인한 사용자가 특정 모임에 참여합니다.")
    @PostMapping("/{meetingId}/join")
    public ApiResponse<MeetingResponse> joinMeeting(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.joinMeeting(meetingId));
    }

    @Operation(summary = "모임 상세 조회", description = "meetingId에 해당하는 모임 상세 정보를 조회합니다.")
    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingDetailResponse> getMeeting(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.getMeeting(meetingId));
    }
}
