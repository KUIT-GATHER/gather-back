package com.gather.gather.domain.post.controller;

import com.gather.gather.domain.post.dto.PostCommentCreateRequest;
import com.gather.gather.domain.post.dto.PostCommentResponse;
import com.gather.gather.domain.post.dto.PostCommentUpdateRequest;
import com.gather.gather.domain.post.service.PostCommentService;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Post Comment", description = "우리모임 게시글 댓글 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/posts/{postId}/comments")
public class PostCommentController {

    private final PostCommentService postCommentService;

    @Operation(
            summary = "댓글 목록 조회",
            description =
                    "게시글의 댓글을 오래된 순으로 페이지 단위 조회합니다. 게시글 열람 권한과 동일하게 미가입자는 공지·후기 게시글의 댓글만 볼 수 있습니다.")
    @GetMapping
    public ApiResponse<PageResponse<PostCommentResponse>> getComments(
            @PathVariable Long meetingId,
            @PathVariable Long postId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        return ApiResponse.success(postCommentService.getComments(meetingId, postId, pageable));
    }

    @Operation(summary = "댓글 작성", description = "모임 가입자만 작성할 수 있습니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostCommentResponse> createComment(
            @PathVariable Long meetingId,
            @PathVariable Long postId,
            @Valid @RequestBody PostCommentCreateRequest request) {
        return ApiResponse.success(postCommentService.createComment(meetingId, postId, request));
    }

    @Operation(summary = "댓글 수정", description = "작성자 본인만 수정할 수 있습니다.")
    @PatchMapping("/{commentId}")
    public ApiResponse<PostCommentResponse> updateComment(
            @PathVariable Long meetingId,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody PostCommentUpdateRequest request) {
        return ApiResponse.success(
                postCommentService.updateComment(meetingId, postId, commentId, request));
    }

    @Operation(summary = "댓글 삭제", description = "작성자 본인 또는 모임장이 삭제할 수 있습니다.")
    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long meetingId, @PathVariable Long postId, @PathVariable Long commentId) {
        postCommentService.deleteComment(meetingId, postId, commentId);
        return ApiResponse.success(null);
    }
}
