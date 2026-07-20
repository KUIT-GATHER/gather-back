package com.gather.gather.domain.auth.kakao.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 카카오 로그인 성공 응답의 상태값. 에러 코드가 아니며 두 값 모두 HTTP 200이다. */
@Schema(description = "카카오 로그인 결과 상태")
public enum SignupStatus {
    @Schema(description = "기존 카카오 회원 로그인 완료")
    LOGIN_COMPLETED,
    @Schema(description = "신규 카카오 회원이며 추가정보 입력 필요")
    ADDITIONAL_INFO_REQUIRED
}
