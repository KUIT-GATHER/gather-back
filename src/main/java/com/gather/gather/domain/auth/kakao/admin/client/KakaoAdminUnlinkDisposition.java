package com.gather.gather.domain.auth.kakao.admin.client;

/** 후속 worker가 문자열이나 HTTP 응답을 재해석하지 않고 처리할 수 있는 unlink 결과 분류. */
public enum KakaoAdminUnlinkDisposition {
    SUCCESS,
    ALREADY_UNLINKED,
    RETRYABLE,
    PERMANENT_CONFIGURATION,
    PERMANENT_REQUEST,
    RESPONSE_FAILURE,
    SECURITY_FAILURE,
    UNKNOWN_PERMANENT
}
