package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "이메일 인증 코드 발송 응답")
public record EmailVerificationSendResponse(
        @Schema(description = "인증 코드를 발송한 이메일", example = "test@example.com") String email,
        @Schema(description = "인증 코드 만료 시각", example = "2026-06-28T12:10:00")
                LocalDateTime expiresAt,
        @Schema(description = "재발송 가능 시각. 이 시각 전에는 재발송이 거부됩니다.", example = "2026-06-28T12:03:00")
                LocalDateTime resendAvailableAt,
        @Schema(description = "처리 메시지", example = "인증 코드가 발송되었습니다.") String message) {}
