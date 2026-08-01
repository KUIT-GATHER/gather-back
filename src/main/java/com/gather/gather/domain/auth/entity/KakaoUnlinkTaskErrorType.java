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
    ATTEMPT_EXHAUSTED;

    public boolean isDeadCompatible() {
        return switch (this) {
            case CONFIGURATION,
                            REQUEST,
                            RESPONSE,
                            SECURITY,
                            UNKNOWN,
                            INVARIANT,
                            ATTEMPT_EXHAUSTED ->
                    true;
            case RETRYABLE, STALE -> false;
        };
    }
}
