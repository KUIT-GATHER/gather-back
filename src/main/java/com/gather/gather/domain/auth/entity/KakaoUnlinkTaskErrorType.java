package com.gather.gather.domain.auth.entity;

public enum KakaoUnlinkTaskErrorType {
    RETRYABLE,
    CONFIGURATION,
    REQUEST,
    RESPONSE,
    SECURITY,
    UNKNOWN,
    STALE,
    INVARIANT,
    ATTEMPT_EXHAUSTED
}
