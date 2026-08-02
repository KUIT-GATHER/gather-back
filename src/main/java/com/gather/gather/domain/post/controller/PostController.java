package com.gather.gather.domain.post.controller;

import com.gather.gather.domain.post.dto.PostCreateRequest;
import com.gather.gather.domain.post.dto.PostResponse;
import com.gather.gather.domain.post.dto.PostSummaryResponse;
import com.gather.gather.domain.post.dto.PostUpdateRequest;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.service.PostService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Post", description = "우리모임 게시판 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/posts")
public class PostController {

    private final PostService postService;

    @Operation(
            summary = "게시판 목록 조회",
            description =
                    """
                    모임 게시판 글 목록을 페이지 단위로 조회합니다. 미가입자는 공지·후기만 조회되며, type 미지정 시 열람 가능한
                    전체 유형을 반환합니다. 기본 정렬은 최신순(createdAt DESC, postId DESC)이며 공통 PageResponse로 반환합니다.
                    """)
    @GetMapping
    public ApiResponse<PageResponse<PostSummaryResponse>> getPosts(
            @PathVariable Long meetingId,
            @RequestParam(required = false) PostType type,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(postService.getPosts(meetingId, type, pageable));
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 상세를 조회합니다. 미가입자는 공지·후기만 열람할 수 있습니다.")
    @GetMapping("/{postId}")
    public ApiResponse<PostResponse> getPost(
            @PathVariable Long meetingId, @PathVariable Long postId) {
        return ApiResponse.success(postService.getPost(meetingId, postId));
    }

    @Operation(summary = "게시글 작성", description = "모임 가입자만 작성할 수 있으며, 공지(NOTICE)는 모임장만 작성할 수 있습니다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostResponse> createPost(
            @PathVariable Long meetingId, @Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.success(postService.createPost(meetingId, request));
    }

    @Operation(summary = "게시글 수정", description = "작성자 본인만 수정할 수 있습니다.")
    @PatchMapping("/{postId}")
    public ApiResponse<PostResponse> updatePost(
            @PathVariable Long meetingId,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request) {
        return ApiResponse.success(postService.updatePost(meetingId, postId, request));
    }

    @Operation(summary = "게시글 삭제", description = "작성자 본인 또는 모임장이 삭제할 수 있습니다.")
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long meetingId, @PathVariable Long postId) {
        postService.deletePost(meetingId, postId);
        return ApiResponse.success(null);
    }
}
