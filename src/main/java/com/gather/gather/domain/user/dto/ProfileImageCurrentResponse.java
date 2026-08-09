package com.gather.gather.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 프로필 이미지 조회 응답")
public record ProfileImageCurrentResponse(
        @Schema(description = "현재 프로필 이미지 공개 URL. 등록된 이미지가 없으면 null입니다.", nullable = true)
                String profileImageUrl) {}
