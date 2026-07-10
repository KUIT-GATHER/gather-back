package com.gather.gather.domain.post.controller;

import com.gather.gather.domain.post.dto.PostCreateRequest;
import com.gather.gather.domain.post.dto.PostResponse;
import com.gather.gather.domain.post.dto.PostSummaryResponse;
import com.gather.gather.domain.post.dto.PostUpdateRequest;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.service.PostService;
import com.gather.gather.global.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/posts")
public class PostController {

    private final PostService postService;

    /** 게시판 목록. 미가입자는 공지·후기만 조회된다. type 미지정 시 열람 가능한 전체 유형. */
    @GetMapping
    public ApiResponse<List<PostSummaryResponse>> getPosts(
            @PathVariable Long meetingId, @RequestParam(required = false) PostType type) {
        return ApiResponse.success(postService.getPosts(meetingId, type));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostResponse> getPost(
            @PathVariable Long meetingId, @PathVariable Long postId) {
        return ApiResponse.success(postService.getPost(meetingId, postId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostResponse> createPost(
            @PathVariable Long meetingId, @Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.success(postService.createPost(meetingId, request));
    }

    @PatchMapping("/{postId}")
    public ApiResponse<PostResponse> updatePost(
            @PathVariable Long meetingId,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request) {
        return ApiResponse.success(postService.updatePost(meetingId, postId, request));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @PathVariable Long meetingId, @PathVariable Long postId) {
        postService.deletePost(meetingId, postId);
        return ApiResponse.success(null);
    }
}