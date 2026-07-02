package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "이메일 인증 코드 확인 응답")
public record EmailVerificationConfirmResponse(
        @Schema(description = "인증 완료 이메일", example = "test@example.com") String email,
        @Schema(description = "인증 완료 여부", example = "true") boolean verified,
        @Schema(description = "이메일 인증 완료 시각", example = "2026-06-28T12:05:00")
                LocalDateTime verifiedAt) {}
