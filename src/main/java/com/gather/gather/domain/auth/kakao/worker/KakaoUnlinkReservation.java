package com.gather.gather.domain.auth.kakao.worker;

public record KakaoUnlinkReservation(Outcome outcome, KakaoUnlinkAttempt attempt) {

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
