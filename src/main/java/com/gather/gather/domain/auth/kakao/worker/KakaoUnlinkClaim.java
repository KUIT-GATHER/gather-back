package com.gather.gather.domain.auth.kakao.worker;

public record KakaoUnlinkClaim(
        Long taskId,
        Long socialAccountId,
        Long userId,
        long generation,
        String claimToken,
        int retryCycle,
        int attemptCount) {}
