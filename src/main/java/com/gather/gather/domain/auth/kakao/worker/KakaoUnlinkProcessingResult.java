package com.gather.gather.domain.auth.kakao.worker;

public enum KakaoUnlinkProcessingResult {
    SUCCEEDED,
    RETRY_SCHEDULED,
    DEAD,
    STALE,
    CLAIM_LOST,
    CONFIGURATION_BLOCKED
}
