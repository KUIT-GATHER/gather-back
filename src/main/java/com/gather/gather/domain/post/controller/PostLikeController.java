package com.gather.gather.domain.post.controller;

import com.gather.gather.domain.post.dto.PostLikeResponse;
import com.gather.gather.domain.post.service.PostLikeService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Post Like", description = "우리모임 게시글 좋아요 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/posts/{postId}/likes")
public class PostLikeController {

    private final PostLikeService postLikeService;

    @Operation(
            summary = "게시글 좋아요 토글",
            description = "모임 가입자가 게시글 좋아요를 등록하거나 취소합니다. 응답의 liked/likeCount로 하트 상태와 카운트를 갱신합니다.")
    @PostMapping
    public ApiResponse<PostLikeResponse> toggleLike(
            @PathVariable Long meetingId, @PathVariable Long postId) {
        return ApiResponse.success(postLikeService.toggleLike(meetingId, postId));
    }
}
