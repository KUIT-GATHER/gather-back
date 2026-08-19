package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "비밀번호 재설정 권한 발급 요청")
public record PasswordResetAuthorityRequest(
        @Schema(
                        description = "RESET_PASSWORD 목적으로 완료한 휴대폰 인증 세션 ID",
                        format = "uuid",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                UUID phoneVerificationId) {}
