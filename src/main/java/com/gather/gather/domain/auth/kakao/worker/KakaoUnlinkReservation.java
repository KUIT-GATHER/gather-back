package com.gather.gather.domain.auth.kakao.worker;

public record KakaoUnlinkReservation(Outcome outcome, KakaoUnlinkAttempt attempt) {

    public KakaoUnlinkReservation {
        if (outcome == null) {
            throw new IllegalArgumentException("reservation outcome은 필수입니다.");
        }
        if (outcome == Outcome.RESERVED && attempt == null) {
            throw new IllegalArgumentException("RESERVED 결과에는 attempt가 필수입니다.");
        }
        if (outcome != Outcome.RESERVED && attempt != null) {
            throw new IllegalArgumentException("RESERVED가 아닌 결과에는 attempt가 없어야 합니다.");
        }
    }

    public enum Outcome {
        RESERVED,
        BLOCKED,
        CLAIM_LOST,
        TERMINAL
    }

    static KakaoUnlinkReservation reserved(KakaoUnlinkAttempt attempt) {
        return new KakaoUnlinkReservation(Outcome.RESERVED, attempt);
    }

    static KakaoUnlinkReservation of(Outcome outcome) {
        return new KakaoUnlinkReservation(outcome, null);
    }
}
