package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이디 찾기 결과")
public record AccountRecoveryResponse(
        @Schema(description = "로그인 방식", example = "EMAIL") AccountLoginType loginType,
        @Schema(description = "이메일 로그인 계정의 가입 이메일", example = "user@example.com") String email) {

    public AccountRecoveryResponse {
        if (loginType == null) {
            throw new IllegalArgumentException("로그인 방식은 필수입니다.");
        }
        boolean hasEmail = email != null && !email.isBlank();
        switch (loginType) {
            case EMAIL -> {
                if (!hasEmail) {
                    throw new IllegalArgumentException("이메일 계정에는 이메일이 필요합니다.");
                }
            }
            case KAKAO -> {
                if (email != null) {
                    throw new IllegalArgumentException("카카오 전용 계정에는 이메일을 포함할 수 없습니다.");
                }
            }
        }
    }

    public static AccountRecoveryResponse email(String email) {
        return new AccountRecoveryResponse(AccountLoginType.EMAIL, email);
    }

    public static AccountRecoveryResponse kakao() {
        return new AccountRecoveryResponse(AccountLoginType.KAKAO, null);
    }
}
