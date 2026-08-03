package com.gather.gather.domain.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Schema(description = "게시글 이미지 Presigned PUT URL 발급 요청")
public record PostImagePresignedUrlRequest(
        @Schema(description = "이미지 MIME 타입", example = "image/jpeg")
                @NotBlank(message = "contentType은 필수입니다.")
                String contentType,
        @Schema(description = "이미지 파일 크기(byte)", example = "1048576")
                @Positive(message = "파일 크기는 1 이상이어야 합니다.")
                long fileSize) {}
