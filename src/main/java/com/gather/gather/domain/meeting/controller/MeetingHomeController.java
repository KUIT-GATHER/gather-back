package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingHomeResponse;
import com.gather.gather.domain.meeting.service.MeetingHomeService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모임 홈 화면 API.
 *
 * <p>기존 {@code MeetingController}와 경로 프리픽스는 공유하지만, 홈 전용 조회만 담당하는 별도 컨트롤러로 분리해 파일 소유를 분명히 한다.
 */
@Tag(name = "Meeting Home", description = "모임 홈 화면 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings")
public class MeetingHomeController {

    private final MeetingHomeService meetingHomeService;

    @Operation(
            summary = "모임 홈 조회",
            description =
                    "모임 홈 화면 정보를 조회합니다. 팀원 목록·연관 공고·다가오는 활동·가입 여부를 함께 반환하며, "
                            + "member=false면 하단 모임 신청 버튼을, host=true면 나의 활동 탭을 노출합니다.")
    @GetMapping("/{meetingId}/home")
    public ApiResponse<MeetingHomeResponse> getMeetingHome(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingHomeService.getMeetingHome(meetingId));
    }
}