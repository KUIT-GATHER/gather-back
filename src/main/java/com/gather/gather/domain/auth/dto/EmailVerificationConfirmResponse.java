package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "이메일 인증 코드 확인 응답")
public record EmailVerificationConfirmResponse(
        @Schema(description = "인증 완료 이메일", example = "test@example.com") String email,
        @Schema(description = "인증 완료 여부", example = "true") boolean verified,
        @Schema(
                        description = "이메일 인증 완료 시각(UTC, offset 없는 ISO-8601)",
                        example = "2026-06-28T03:05:00")
                LocalDateTime verifiedAt,
        @Schema(
                        description = "회원가입에 사용할 이메일 인증 결과 ID",
                        format = "uuid",
                        example = "98fa88ef-bbeb-4928-a202-7885197b3774")
                UUID emailVerificationId) {}
