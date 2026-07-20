package com.gather.gather.domain.auth.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 카카오 사용자 정보 조회 응답. 동의항목이 profile_nickname뿐이라 회원번호와 닉네임만 매핑한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(Profile profile) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Profile(String nickname) {}
    }

    /** 사용자가 닉네임 동의를 철회했거나 프로필이 없으면 null이다. 닉네임은 초깃값 용도라 없어도 가입을 막지 않는다. */
    public String nickname() {
        if (kakaoAccount == null || kakaoAccount.profile() == null) {
            return null;
        }
        return kakaoAccount.profile().nickname();
    }
}
