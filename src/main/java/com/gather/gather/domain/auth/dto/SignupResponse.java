package com.gather.gather.domain.auth.dto;

import com.gather.gather.domain.auth.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답")
public record SignupResponse(
        @Schema(description = "생성된 사용자 ID", example = "1") Long userId,
        @Schema(description = "가입 이메일", example = "test@example.com") String email,
        @Schema(description = "사용자 이름", example = "홍길동") String name,
        @Schema(description = "사용자 닉네임", example = "길동") String nickname,
        @Schema(description = "API 인증에 사용할 Access Token", example = "access-token-value")
                String accessToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType) {

    public static SignupResponse bearer(User user, String accessToken) {
        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                accessToken,
                "Bearer");
    }
}
