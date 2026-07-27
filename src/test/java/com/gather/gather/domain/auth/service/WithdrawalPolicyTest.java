package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WithdrawalPolicyTest {

    private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 7, 20, 12, 0);

    private final WithdrawalPolicy withdrawalPolicy = new WithdrawalPolicy();

    @Test
    @DisplayName("6일 23시간은 아직 유예 기간이다")
    void isGracePeriodOver_beforeSevenDays_returnsFalse() {
        LocalDateTime now = WITHDRAWN_AT.plusDays(6).plusHours(23);

        assertThat(withdrawalPolicy.isGracePeriodOver(WITHDRAWN_AT, now)).isFalse();
    }

    @Test
    @DisplayName("정확히 7일이면 유예가 끝난 것으로 본다")
    void isGracePeriodOver_exactlySevenDays_returnsTrue() {
        LocalDateTime now = WITHDRAWN_AT.plusDays(7);

        assertThat(withdrawalPolicy.isGracePeriodOver(WITHDRAWN_AT, now)).isTrue();
    }

    @Test
    @DisplayName("7일 1분이 지나면 유예가 끝났다")
    void isGracePeriodOver_afterSevenDays_returnsTrue() {
        LocalDateTime now = WITHDRAWN_AT.plusDays(7).plusMinutes(1);

        assertThat(withdrawalPolicy.isGracePeriodOver(WITHDRAWN_AT, now)).isTrue();
    }

    @Test
    @DisplayName("조회 기준 시각은 현재보다 7일 이전이다")
    void graceExpiryThreshold_isSevenDaysBeforeNow() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 27, 4, 30);

        assertThat(withdrawalPolicy.graceExpiryThreshold(now))
                .isEqualTo(LocalDateTime.of(2026, 7, 20, 4, 30));
    }
}
