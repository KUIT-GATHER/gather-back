package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 응답")
public record TokenResponse(
        @Schema(
                        description =
                                "API 인증에 사용할 Access Token (JWT). Authorization: Bearer <token> 형식으로 전송",
                        example =
                                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlVTRVIifQ.signature")
                String accessToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType) {

    public static TokenResponse bearer(String accessToken) {
        return new TokenResponse(accessToken, "Bearer");
    }
}
