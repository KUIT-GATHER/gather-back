package com.gather.gather.domain.post.controller;

import com.gather.gather.domain.post.dto.MyMeetingActivitySummaryResponse;
import com.gather.gather.domain.post.dto.PostSummaryResponse;
import com.gather.gather.domain.post.service.MyMeetingActivityService;
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
}
