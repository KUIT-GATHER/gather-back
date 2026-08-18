package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "비밀번호 재설정 권한 발급 결과")
public record PasswordResetTokenResponse(
        @Schema(description = "비밀번호 재설정 토큰. 발급 후 10분간 유효하며 1회만 사용할 수 있습니다.")
                String passwordResetToken,
        @Schema(
                        description = "비밀번호 재설정 토큰 만료 시각(UTC, offset 없는 ISO-8601)",
                        example = "2026-08-18T04:10:00")
                LocalDateTime expiresAt) {

    public PasswordResetTokenResponse {
        if (passwordResetToken == null || passwordResetToken.isBlank()) {
            throw new IllegalArgumentException("비밀번호 재설정 토큰은 필수입니다.");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("비밀번호 재설정 토큰 만료 시각은 필수입니다.");
        }
    }

    /** 로그나 예외 메시지에 raw token이 실리지 않도록 값을 감춘다. */
    @Override
    public String toString() {
        return "PasswordResetTokenResponse[passwordResetToken=***, expiresAt=" + expiresAt + "]";
    }
}
