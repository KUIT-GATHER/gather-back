package com.gather.gather.domain.auth.dto;

import com.gather.gather.domain.auth.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 응답")
public record SignupResponse(
        @Schema(description = "생성된 사용자 ID", example = "1") Long userId,
        @Schema(description = "가입 이메일", example = "test@example.com") String email,
        @Schema(description = "사용자 이름", example = "홍길동") String name,
        @Schema(description = "사용자 닉네임", example = "길동") String nickname) {

    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(), user.getEmail(), user.getName(), user.getNickname());
    }
}
