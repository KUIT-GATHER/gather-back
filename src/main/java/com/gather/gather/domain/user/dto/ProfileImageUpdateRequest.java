package com.gather.gather.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "업로드 완료된 프로필 이미지 반영 요청")
public record ProfileImageUpdateRequest(
        @Schema(
                        description = "Presigned URL 발급 응답으로 받은 S3 객체 키",
                        example = "profiles/15/550e8400-e29b-41d4-a716-446655440000.jpg")
                @NotBlank
                String objectKey) {}
