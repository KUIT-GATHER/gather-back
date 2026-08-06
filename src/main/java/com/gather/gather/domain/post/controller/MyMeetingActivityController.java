package com.gather.gather.domain.post.controller;

import com.gather.gather.domain.post.dto.MyMeetingActivitySummaryResponse;
import com.gather.gather.domain.post.dto.PostSummaryResponse;
import com.gather.gather.domain.post.service.MyMeetingActivityService;
import com.gather.gather.domain.recruit.dto.MyAppliedRecruitResponse;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My Meeting Activity", description = "모임 내부 '나의 활동' 탭 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/my")
public class MyMeetingActivityController {

    private final MyMeetingActivityService myMeetingActivityService;

    @Operation(
            summary = "나의 활동 요약",
            description = "나의 활동 탭 상단의 개수(작성한 게시글/댓글 단 게시글)를 반환합니다. 가입자 전용입니다.")
    @GetMapping("/activity-summary")
    public ApiResponse<MyMeetingActivitySummaryResponse> getActivitySummary(
            @PathVariable Long meetingId) {
        return ApiResponse.success(myMeetingActivityService.getActivitySummary(meetingId));
    }

    @Operation(
            summary = "내가 작성한 게시글 목록",
            description = "이 모임에서 내가 작성한 게시글을 최신순으로 페이지 조회합니다. 가입자 전용입니다.")
    @GetMapping("/posts")
    public ApiResponse<PageResponse<PostSummaryResponse>> getMyPosts(
            @PathVariable Long meetingId,
            @PageableDefault(
                            size = 20,
                            sort = {"createdAt", "id"},
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.success(myMeetingActivityService.getMyPosts(meetingId, pageable));
    }

    @Operation(
            summary = "내가 댓글 단 게시글 목록",
            description = "이 모임에서 내가 댓글을 단 게시글을 최신순으로 페이지 조회합니다. 가입자 전용입니다.")
    @GetMapping("/commented-posts")
    public ApiResponse<PageResponse<PostSummaryResponse>> getMyCommentedPosts(
            @PathVariable Long meetingId,
            @PageableDefault(
                            size = 20,
                            sort = {"createdAt", "id"},
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.success(
                myMeetingActivityService.getMyCommentedPosts(meetingId, pageable));
    }

    @Operation(
            summary = "내가 신청한 봉사 목록",
            description =
                    "이 모임에서 내가 참여신청한 모집공고를 활동일 최신순으로 페이지 조회합니다. 가입자 전용입니다. "
                            + "status는 참여 상태(APPLIED/CONFIRMED/COMPLETED/REVIEWED)이며, 모임장이 모임을 완료 처리하기 전까지는"
                            + " APPLIED, 완료 처리 이후에는 COMPLETED로 내려옵니다. CONFIRMED·REVIEWED는 참여 승인·후기 작성 기능이 아직 없어"
                            + " 현재는 내려오지 않는 예약값입니다. 화면에는 APPLIED/CONFIRMED를 \"신청중\", COMPLETED/REVIEWED를 \"봉사"
                            + " 완료\"로 묶어 표시하면 됩니다.")
    @GetMapping("/applied-recruits")
    public ApiResponse<PageResponse<MyAppliedRecruitResponse>> getMyAppliedRecruits(
            @PathVariable Long meetingId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(
                myMeetingActivityService.getMyAppliedRecruits(meetingId, pageable));
    }
}
