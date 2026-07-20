package com.gather.gather.domain.auth.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 토큰 교환 응답. refresh_token·expires_in 등 나머지 필드는 저장하지 않으므로 매핑하지 않는다.
 *
 * <p>카카오 Access Token은 사용자 정보 조회에만 쓰고 보관하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {}
