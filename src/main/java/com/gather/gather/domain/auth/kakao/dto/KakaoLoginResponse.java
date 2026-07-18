package com.gather.gather.domain.auth.kakao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** 기존 회원 로그인과 신규 회원 추가정보 필요를 하나의 200 응답으로 표현한다. 상태에 해당하지 않는 필드는 응답에서 제외된다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "카카오 로그인 응답")
public record KakaoLoginResponse(
        @Schema(description = "로그인 결과 상태") SignupStatus signupStatus,
        @Schema(description = "Gather Access Token. LOGIN_COMPLETED일 때만 내려갑니다.") String accessToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
        @Schema(description = "가입용 임시 토큰. ADDITIONAL_INFO_REQUIRED일 때만 내려갑니다.") String signupToken,
        @Schema(description = "카카오 프로필. ADDITIONAL_INFO_REQUIRED일 때만 내려갑니다.")
                KakaoProfile profile) {

    public static KakaoLoginResponse loginCompleted(String accessToken) {
        return new KakaoLoginResponse(
                SignupStatus.LOGIN_COMPLETED, accessToken, "Bearer", null, null);
    }

    public static KakaoLoginResponse additionalInfoRequired(String signupToken, String nickname) {
        return new KakaoLoginResponse(
                SignupStatus.ADDITIONAL_INFO_REQUIRED,
                null,
                null,
                signupToken,
                new KakaoProfile(nickname));
    }

    @Schema(description = "추가정보 화면의 닉네임 초깃값 용도")
    public record KakaoProfile(
            @Schema(description = "카카오 닉네임. 없으면 null입니다.", example = "동현") String nickname) {}
}
