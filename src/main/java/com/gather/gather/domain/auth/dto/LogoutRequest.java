package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그아웃 요청")
public record LogoutRequest(
        @Schema(description = "폐기할 Refresh Token", example = "refresh-token-value") @NotBlank
                String refreshToken) {}
