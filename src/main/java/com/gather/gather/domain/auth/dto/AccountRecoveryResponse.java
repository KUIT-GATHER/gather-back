package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이디 찾기 결과")
public record AccountRecoveryResponse(
        @Schema(description = "로그인 방식", example = "EMAIL") AccountLoginType loginType,
        @Schema(description = "이메일 로그인 계정의 가입 이메일", example = "user@example.com") String email) {

    public static AccountRecoveryResponse email(String email) {
        return new AccountRecoveryResponse(AccountLoginType.EMAIL, email);
    }

    public static AccountRecoveryResponse kakao() {
        return new AccountRecoveryResponse(AccountLoginType.KAKAO, null);
    }
}
