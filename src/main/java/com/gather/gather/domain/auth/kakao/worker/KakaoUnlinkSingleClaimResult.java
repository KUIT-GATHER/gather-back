package com.gather.gather.domain.auth.kakao.worker;

public record KakaoUnlinkSingleClaimResult(Outcome outcome, KakaoUnlinkClaim claim) {

    public KakaoUnlinkSingleClaimResult {
        if (outcome == null) {
            throw new IllegalArgumentException("single claim outcome은 필수입니다.");
        }
        if (outcome == Outcome.CLAIMED && claim == null) {
            throw new IllegalArgumentException("CLAIMED 결과에는 claim이 필수입니다.");
        }
        if (outcome != Outcome.CLAIMED && claim != null) {
            throw new IllegalArgumentException("CLAIMED가 아닌 결과에는 claim이 없어야 합니다.");
        }
    }

    public static KakaoUnlinkSingleClaimResult claimed(KakaoUnlinkClaim claim) {
        return new KakaoUnlinkSingleClaimResult(Outcome.CLAIMED, claim);
    }

    public static KakaoUnlinkSingleClaimResult of(Outcome outcome) {
        return new KakaoUnlinkSingleClaimResult(outcome, null);
    }

    public enum Outcome {
        CLAIMED,
        TASK_NOT_FOUND,
        NOT_PENDING,
        NOT_DUE,
        LOCK_CONFLICT,
        CONTROL_BLOCKED,
        INVARIANT_ERROR
    }
}
