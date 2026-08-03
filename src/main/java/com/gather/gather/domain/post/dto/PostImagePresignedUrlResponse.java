package com.gather.gather.domain.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게시글 이미지 Presigned PUT URL 발급 응답")
public record PostImagePresignedUrlResponse(
        @Schema(description = "이미지를 PUT 방식으로 직접 업로드할 Presigned URL") String uploadUrl,
        @Schema(description = "게시글 작성/수정 API의 imageObjectKeys에 넣을 S3 객체 키") String objectKey,
        @Schema(description = "버킷 정책으로 공개 조회 가능한 이미지 URL") String publicUrl,
        @Schema(description = "Presigned URL 만료 시간(초)", example = "300") long expiresInSeconds) {}
