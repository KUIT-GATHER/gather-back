package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailVerificationTest {

    private static final String EMAIL = "test@example.com";

    private EmailVerification create() {
        return EmailVerification.create(EMAIL, "111111", LocalDateTime.now().plusMinutes(10));
    }

    @Test
    @DisplayName("최초 생성 시 당일 발송 횟수는 1, 시도 횟수는 0이다")
    void create_initializesCounts() {
        EmailVerification verification = create();

        assertThat(verification.dailySendCountAsOf(verification.getCreatedAt().toLocalDate()))
                .isEqualTo(1);
        assertThat(verification.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("같은 날 재발송하면 당일 발송 횟수가 누적된다")
    void refresh_sameDay_incrementsDailyCount() {
        EmailVerification verification = create();

        verification.refresh("222222", LocalDateTime.now().plusMinutes(10));

        assertThat(verification.getDailySendCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("마지막 발송이 다른 날이면 당일 발송 횟수는 0으로 계산된다")
    void dailySendCountAsOf_differentDay_returnsZero() {
        EmailVerification verification = create();

        assertThat(
                        verification.dailySendCountAsOf(
                                verification.getCreatedAt().toLocalDate().plusDays(1)))
                .isZero();
    }

    @Test
    @DisplayName("재발송하면 시도 횟수와 인증 완료 상태가 초기화된다")
    void refresh_resetsAttemptAndVerified() {
        EmailVerification verification = create();
        verification.verify(LocalDateTime.now());
        verification.increaseAttempt();

        verification.refresh("222222", LocalDateTime.now().plusMinutes(10));

        assertThat(verification.isVerified()).isFalse();
        assertThat(verification.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("쿨다운 시간 이내면 재발송이 막히고 지나면 허용된다")
    void isWithinResendCooldown_boundary() {
        EmailVerification verification = create();
        LocalDateTime created = verification.getCreatedAt();

        assertThat(verification.isWithinResendCooldown(created.plusMinutes(2), 3)).isTrue();
        assertThat(verification.isWithinResendCooldown(created.plusMinutes(3).plusSeconds(1), 3))
                .isFalse();
    }

    @Test
    @DisplayName("시도 횟수가 한도 이상이면 초과로 판정한다")
    void isAttemptExceeded_boundary() {
        EmailVerification verification = create();

        for (int i = 0; i < 5; i++) {
            assertThat(verification.isAttemptExceeded(5)).isFalse();
            verification.increaseAttempt();
        }

        assertThat(verification.isAttemptExceeded(5)).isTrue();
    }
}
