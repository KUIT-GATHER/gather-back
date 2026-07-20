package com.gather.gather.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "프로필 사진 업로드 URL 발급 요청")
public record ProfileImageUploadUrlRequest(
        @Schema(description = "업로드할 파일 확장자", example = "jpg")
                @NotBlank
                @Pattern(regexp = "^(?i)(jpg|jpeg|png|webp)$")
                String fileExtension,
        @Schema(description = "업로드할 파일의 Content-Type", example = "image/jpeg")
                @NotBlank
                @Pattern(regexp = "^image/(jpeg|png|webp)$")
                String contentType) {}
