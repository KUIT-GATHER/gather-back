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

    public KakaoAdminUnlinkResult {
        if (disposition == null) {
            throw new IllegalArgumentException("unlink disposition은 필수입니다.");
        }
        if (retryAfterAt != null && disposition != KakaoAdminUnlinkDisposition.RETRYABLE) {
            throw new IllegalArgumentException("Retry-After는 RETRYABLE 결과에만 허용됩니다.");
        }
        if (retryAfterAt != null
                && (httpStatus == null
                        || (httpStatus != 429 && (httpStatus < 500 || httpStatus >= 600)))) {
            throw new IllegalArgumentException("Retry-After는 HTTP 429 또는 5xx 결과에만 허용됩니다.");
        }
    }

    static KakaoAdminUnlinkResult of(
            KakaoAdminUnlinkDisposition disposition,
            Integer httpStatus,
            Integer kakaoCode,
            Instant retryAfterAt) {
        return new KakaoAdminUnlinkResult(disposition, httpStatus, kakaoCode, retryAfterAt);
    }
}
