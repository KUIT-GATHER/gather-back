package com.gather.gather.domain.post.controller;

import com.gather.gather.domain.post.dto.PostImagePresignedUrlRequest;
import com.gather.gather.domain.post.dto.PostImagePresignedUrlResponse;
import com.gather.gather.domain.post.service.PostImageService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Post Image", description = "우리모임 게시글 이미지 S3 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/posts/images")
public class PostImageController {

    private final PostImageService postImageService;

    @Operation(
            summary = "게시글 이미지 Presigned PUT URL 발급",
            description =
                    """
                    JPEG/PNG/WebP 이미지를 업로드할 presigned URL을 발급합니다(게시글당 최대 3장).
                    프론트는 uploadUrl로 PUT 업로드 후, 받은 objectKey들을 게시글 작성/수정 API의
                    imageObjectKeys에 담아 전송하면 게시글에 반영됩니다.
                    """)
    @PostMapping("/presigned-url")
    public ApiResponse<PostImagePresignedUrlResponse> createPresignedUrl(
            @PathVariable Long meetingId,
            @Valid @RequestBody PostImagePresignedUrlRequest request) {
        return ApiResponse.success(postImageService.createPresignedUrl(request));
    }
}
