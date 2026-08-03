package com.gather.gather.domain.recruit.controller;

import com.gather.gather.domain.recruit.dto.RecruitCreateRequest;
import com.gather.gather.domain.recruit.dto.RecruitDetailResponse;
import com.gather.gather.domain.recruit.dto.RecruitParticipationResponse;
import com.gather.gather.domain.recruit.service.MeetingRecruitService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting Recruit", description = "모임 내부 모집공고(RECRUIT) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/posts")
public class MeetingRecruitController {

    private final MeetingRecruitService meetingRecruitService;

    @Operation(
            summary = "모집공고 작성",
            description = "모임장만 작성할 수 있습니다. RECRUIT 게시글과 모집 확장 정보(장소·일정·정원·카테고리·마감일 등)를 함께 생성합니다.")
    @PostMapping("/recruits")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecruitDetailResponse> createRecruit(
            @PathVariable Long meetingId, @Valid @RequestBody RecruitCreateRequest request) {
        return ApiResponse.success(meetingRecruitService.createRecruit(meetingId, request));
    }

    @Operation(
            summary = "모집공고 상세 조회",
            description = "기본 게시글 정보에 모집 확장 필드와 참여 현황(n/N)·내 신청 상태를 더해 반환합니다. 가입자만 조회할 수 있습니다.")
    @GetMapping("/{postId}/recruit")
    public ApiResponse<RecruitDetailResponse> getRecruit(
            @PathVariable Long meetingId, @PathVariable Long postId) {
        return ApiResponse.success(meetingRecruitService.getRecruit(meetingId, postId));
    }

    @Operation(
            summary = "참여신청 토글",
            description =
                    "가입자가 모집공고에 참여신청하거나 취소합니다. 신청 마감일 이후에는 변경할 수 없고, 정원을 초과해 신청할 수 없습니다."
                            + " 응답의 applied/appliedCount로 버튼과 현황을 갱신합니다.")
    @PostMapping("/{postId}/recruit/participation")
    public ApiResponse<RecruitParticipationResponse> toggleParticipation(
            @PathVariable Long meetingId, @PathVariable Long postId) {
        return ApiResponse.success(meetingRecruitService.toggleParticipation(meetingId, postId));
    }
}
