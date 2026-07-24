package com.gather.gather.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "프로필 이미지 Presigned PUT URL 발급 요청")
public record ProfileImagePresignedUrlRequest(
        @Schema(
                        description =
                                "업로드할 이미지 MIME type. image/jpeg, image/png, image/webp만 허용합니다.",
                        example = "image/jpeg")
                @NotBlank
                String contentType,
        @Schema(description = "업로드할 파일의 바이트 크기. 최대 5MB입니다.", example = "1048576") @NotNull @Positive
                Long fileSize) {}
