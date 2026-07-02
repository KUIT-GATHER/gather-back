package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 응답")
public record TokenResponse(
        @Schema(description = "API 인증에 사용할 Access Token", example = "access-token-value")
                String accessToken,
        @Schema(description = "Access Token 재발급에 사용할 Refresh Token", example = "refresh-token-value")
                String refreshToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType) {

    public static TokenResponse bearer(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken, "Bearer");
    }
}
