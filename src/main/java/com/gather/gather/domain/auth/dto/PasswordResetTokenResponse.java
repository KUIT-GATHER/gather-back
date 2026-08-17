package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "비밀번호 재설정 권한 발급 결과")
public record PasswordResetTokenResponse(
        @Schema(description = "비밀번호 재설정 토큰. 발급 후 10분간 유효하며 1회만 사용할 수 있습니다.")
                String passwordResetToken) {

    public PasswordResetTokenResponse {
        if (passwordResetToken == null || passwordResetToken.isBlank()) {
            throw new IllegalArgumentException("비밀번호 재설정 토큰은 필수입니다.");
        }
    }

    /** 로그나 예외 메시지에 raw token이 실리지 않도록 값을 감춘다. */
    @Override
    public String toString() {
        return "PasswordResetTokenResponse[passwordResetToken=***]";
    }
}
