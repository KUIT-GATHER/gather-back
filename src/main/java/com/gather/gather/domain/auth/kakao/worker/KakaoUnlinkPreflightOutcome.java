package com.gather.gather.domain.auth.kakao.worker;

public enum KakaoUnlinkPreflightOutcome {
    RESERVE,
    LOCAL_FINALIZE,
    STALE,
    CLAIM_LOST
}
