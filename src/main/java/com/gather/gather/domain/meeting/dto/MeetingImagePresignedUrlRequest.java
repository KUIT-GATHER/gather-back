package com.gather.gather.domain.meeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "모임 이미지 Presigned PUT URL 발급 요청")
public record MeetingImagePresignedUrlRequest(
        @Schema(description = "이미지 MIME type", example = "image/jpeg") @NotBlank String contentType,
        @Schema(description = "이미지 파일 크기(byte)", example = "1048576") @NotNull @Positive Long fileSize) {}