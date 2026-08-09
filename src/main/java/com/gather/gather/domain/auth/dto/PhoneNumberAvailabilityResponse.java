package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전화번호 중복 확인 응답")
public record PhoneNumberAvailabilityResponse(
        @Schema(description = "정규화된 전화번호", example = "01012345678") String phoneNumber,
        @Schema(description = "true면 가입 가능, false면 이미 사용 중이거나 탈퇴 후 재가입 제한 중입니다.", example = "true")
                boolean available) {}
