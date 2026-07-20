package com.gather.gather.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로필 사진 업로드 URL 발급 응답")
public record ProfileImageUploadUrlResponse(
        @Schema(
                        description = "presigned PUT URL. 이 URL로 파일을 직접 PUT 업로드합니다.",
                        example =
                                "https://gather-profile-images.s3.ap-northeast-2.amazonaws.com/profiles/1/uuid.jpg?mock-presigned=true")
                String uploadUrl,
        @Schema(
                        description =
                                "업로드 완료 후 PATCH /api/v1/users/me의 profileImageKey에 그대로 넣어 저장할 오브젝트 키",
                        example = "profiles/1/3f9a2b1c-....jpg")
                String objectKey,
        @Schema(description = "업로드 URL 유효 시간(초)", example = "300") long expiresInSeconds) {}
