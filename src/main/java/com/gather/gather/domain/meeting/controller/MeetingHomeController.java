package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingHomeResponse;
import com.gather.gather.domain.meeting.service.MeetingHomeService;
import com.gather.gather.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모임 홈 화면 API(현승 담당).
 *
 * <p>기존 {@code MeetingController}(연석)와 경로 프리픽스는 공유하지만, 홈 전용 조회만 담당하는 별도 컨트롤러로 분리해 파일 소유를 분명히 한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingHomeController {

    private final MeetingHomeService meetingHomeService;

    @GetMapping("/{meetingId}/home")
    public ApiResponse<MeetingHomeResponse> getMeetingHome(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingHomeService.getMeetingHome(meetingId));
    }
}
