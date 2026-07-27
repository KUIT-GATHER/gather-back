package com.gather.gather.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로필 이미지 Presigned PUT URL 발급 응답")
public record ProfileImagePresignedUrlResponse(
        @Schema(description = "이미지 파일을 PUT 방식으로 직접 업로드할 Presigned URL") String uploadUrl,
        @Schema(description = "프로필 이미지 반영 API에 전달할 S3 객체 키") String objectKey,
        @Schema(description = "버킷 정책으로 공개 조회 가능한 이미지 URL") String publicUrl,
        @Schema(description = "Presigned URL 만료 시간(초)", example = "300") long expiresInSeconds) {}
