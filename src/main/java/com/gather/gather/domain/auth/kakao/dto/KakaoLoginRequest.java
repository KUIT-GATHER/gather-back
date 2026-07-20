package com.gather.gather.domain.auth.kakao.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청")
public record KakaoLoginRequest(
        @Schema(
                        description = "카카오 콜백으로 전달받은 인가 코드. 한 번 사용하면 재사용할 수 없습니다.",
                        example = "R0-abcdefghijklmnopqrstuvwxyz1234567890")
                @NotBlank
                String authorizationCode,
        @Schema(
                        description = "인가 요청에 사용한 Redirect URI. 서버 허용 목록과 문자열까지 정확히 일치해야 합니다.",
                        example = "https://gathernow.kr/login/kakao/callback")
                @NotBlank
                String redirectUri) {}
