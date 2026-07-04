package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingCreateRequest;
import com.gather.gather.domain.meeting.dto.MeetingDetailResponse;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.service.MeetingService;
import com.gather.gather.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    public ApiResponse<MeetingResponse> createMeeting(
            @Valid @RequestBody MeetingCreateRequest request) {
        return ApiResponse.success(meetingService.createMeeting(request));
    }

    @GetMapping
    public ApiResponse<List<MeetingResponse>> getMeetings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) MeetingStatus status) {
        return ApiResponse.success(
                meetingService.getMeetings(keyword, regionId, categoryId, status));
    }

    @GetMapping("/my")
    public ApiResponse<List<MeetingResponse>> getMyMeetings() {
        return ApiResponse.success(meetingService.getMyMeetings());
    }

    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingDetailResponse> getMeeting(
            @PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.getMeeting(meetingId));
    }

    @PostMapping("/{meetingId}/join")
    public ApiResponse<MeetingResponse> joinMeeting(
            @PathVariable Long meetingId) {
        return ApiResponse.success(meetingService.joinMeeting(meetingId));
    }
}