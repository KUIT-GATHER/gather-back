package com.gather.gather.domain.auth.kakao.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KakaoUnlinkReservationTest {

    private static final KakaoUnlinkClaim CLAIM =
            new KakaoUnlinkClaim(1L, 2L, 3L, 1L, "opaque-token", 0);
    private static final KakaoUnlinkAttempt ATTEMPT = new KakaoUnlinkAttempt(CLAIM, 123L, 1);

    @Test
    void staticFactories_createEveryValidOutcome() {
        assertThat(KakaoUnlinkReservation.reserved(ATTEMPT))
                .isEqualTo(
                        new KakaoUnlinkReservation(
                                KakaoUnlinkReservation.Outcome.RESERVED, ATTEMPT));

        for (KakaoUnlinkReservation.Outcome outcome :
                new KakaoUnlinkReservation.Outcome[] {
                    KakaoUnlinkReservation.Outcome.BLOCKED,
                    KakaoUnlinkReservation.Outcome.CLAIM_LOST,
                    KakaoUnlinkReservation.Outcome.STALE,
                    KakaoUnlinkReservation.Outcome.DEAD
                }) {
            assertThat(KakaoUnlinkReservation.of(outcome))
                    .isEqualTo(new KakaoUnlinkReservation(outcome, null));
        }
    }

    @Test
    void canonicalConstructor_rejectsMissingOutcome() {
        assertThatThrownBy(() -> new KakaoUnlinkReservation(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outcome");
    }

    @Test
    void canonicalConstructor_rejectsReservedWithoutAttempt() {
        assertThatThrownBy(
                        () ->
                                new KakaoUnlinkReservation(
                                        KakaoUnlinkReservation.Outcome.RESERVED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
    }

    @Test
    void canonicalConstructor_rejectsAttemptForNonReservedOutcome() {
        assertThatThrownBy(
                        () ->
                                new KakaoUnlinkReservation(
                                        KakaoUnlinkReservation.Outcome.BLOCKED, ATTEMPT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
    }
}
