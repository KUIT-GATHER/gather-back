package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "아이디 찾기 요청")
public record AccountRecoveryRequest(
        @Schema(
                        description = "FIND_ACCOUNT 목적으로 완료한 휴대폰 인증 세션 ID",
                        format = "uuid",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                UUID phoneVerificationId) {}
