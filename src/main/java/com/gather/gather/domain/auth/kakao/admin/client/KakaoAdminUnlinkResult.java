package com.gather.gather.domain.auth.kakao.admin.client;

import java.time.Instant;

/**
 * 카카오 unlink 호출 결과.
 *
 * <p>카카오 회원번호, Admin key, 원문 응답과 예외는 보안상 보관하지 않는다.
 */
public record KakaoAdminUnlinkResult(
        KakaoAdminUnlinkDisposition disposition,
        Integer httpStatus,
        Integer kakaoCode,
        Instant retryAfterAt) {

    static KakaoAdminUnlinkResult of(
            KakaoAdminUnlinkDisposition disposition,
            Integer httpStatus,
            Integer kakaoCode,
            Instant retryAfterAt) {
        return new KakaoAdminUnlinkResult(disposition, httpStatus, kakaoCode, retryAfterAt);
    }
}
