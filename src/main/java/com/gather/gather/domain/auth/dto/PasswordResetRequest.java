package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 비밀번호 재설정 요청.
 *
 * <p>토큰 형식 오류는 PASSWORD_RESET_TOKEN_INVALID, 비밀번호 정책 위반은 VALIDATION_ERROR로 응답해야 하므로 Bean Validation
 * 대신 서비스 계층의 정책 검증을 사용한다.
 */
@Schema(description = "비밀번호 재설정 요청")
public record PasswordResetRequest(
        @Schema(
                        description = "비밀번호 재설정 권한 발급 API가 반환한 토큰",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String passwordResetToken,
        @Schema(
                        description = "새 비밀번호. 공백 없이 6자 이상 12자 이하입니다.",
                        example = "password123!",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String password,
        @Schema(
                        description = "새 비밀번호 확인",
                        example = "password123!",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String passwordConfirm) {

    /** 로그나 예외 메시지에 토큰·비밀번호 평문이 실리지 않도록 값을 감춘다. */
    @Override
    public String toString() {
        return "PasswordResetRequest[passwordResetToken=***, password=***, passwordConfirm=***]";
    }
}
