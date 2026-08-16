package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "앱 초기화 시 로그인 세션 복원 결과")
public record SessionRestoreResponse(
        @Schema(description = "세션 복원 여부", example = "true") boolean authenticated,
        @Schema(
                        description = "복원된 세션의 Access Token. 세션이 없으면 null",
                        example = "new-access-token-value",
                        nullable = true)
                String accessToken,
        @Schema(description = "토큰 타입. 세션이 없으면 null", example = "Bearer", nullable = true)
                String tokenType) {

    public static SessionRestoreResponse authenticated(String accessToken) {
        return new SessionRestoreResponse(true, accessToken, "Bearer");
    }

    public static SessionRestoreResponse anonymous() {
        return new SessionRestoreResponse(false, null, null);
    }
}
