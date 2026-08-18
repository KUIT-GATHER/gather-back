package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 로그인 상태 비밀번호 변경 요청.
 *
 * <p>비밀번호 정책 위반은 VALIDATION_ERROR, 확인값 불일치는 PASSWORD_MISMATCH로 구분해 응답해야 하므로 Bean Validation 대신 서비스
 * 계층의 {@code PasswordPolicy}를 사용한다.
 */
@Schema(description = "로그인 상태 비밀번호 변경 요청")
public record PasswordChangeRequest(
        @Schema(description = "현재 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
                String currentPassword,
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

    /** 로그나 예외 메시지에 비밀번호 평문이 실리지 않도록 값을 감춘다. */
    @Override
    public String toString() {
        return "PasswordChangeRequest[currentPassword=***, password=***, passwordConfirm=***]";
    }
}
