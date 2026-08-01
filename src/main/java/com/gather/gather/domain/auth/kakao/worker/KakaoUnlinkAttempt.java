package com.gather.gather.domain.auth.kakao.worker;

public record KakaoUnlinkAttempt(KakaoUnlinkClaim claim, long kakaoUserId, int attemptCount) {}
