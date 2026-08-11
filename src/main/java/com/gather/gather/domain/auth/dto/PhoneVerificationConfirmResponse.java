package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "휴대폰 문자 인증 확인 결과")
public record PhoneVerificationConfirmResponse(
        @Schema(description = "문자 수신 대기 또는 인증 완료 상태", example = "VERIFIED")
                PhoneVerificationStatus status) {}
