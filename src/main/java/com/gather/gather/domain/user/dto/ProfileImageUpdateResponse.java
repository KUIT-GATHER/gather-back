package com.gather.gather.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로필 이미지 반영 응답")
public record ProfileImageUpdateResponse(
        @Schema(description = "프론트에서 바로 조회할 수 있는 공개 프로필 이미지 URL") String profileImageUrl) {}
