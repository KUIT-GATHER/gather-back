package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailVerificationTest {

    private static final String EMAIL = "test@example.com";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 10, 0);

    private EmailVerification create() {
        return EmailVerification.create(
                EMAIL, UUID.randomUUID().toString(), "a".repeat(64), NOW.plusMinutes(10), NOW);
    }

    @Test
    @DisplayName("생성과 재발송 모두 평문 code를 비우고 해시만 보관한다")
    void createAndRefresh_keepOnlyCodeHash() {
        EmailVerification verification = create();

        assertThat(verification.getCode()).isEmpty();
        assertThat(verification.getCodeHash()).isEqualTo("a".repeat(64));
        assertThat(verification.isLegacyFormat()).isFalse();

        verification.refresh(
                UUID.randomUUID().toString(),
                "b".repeat(64),
                NOW.plusMinutes(15),
                NOW.plusMinutes(5));

        assertThat(verification.getCode()).isEmpty();
        assertThat(verification.getCodeHash()).isEqualTo("b".repeat(64));
        assertThat(verification.isLegacyFormat()).isFalse();
    }

    @Test
    @DisplayName("평문 code가 남아 있거나 해시가 없으면 구 버전 형식으로 판정한다")
    void isLegacyFormat_detectsPlaintextCodeOrMissingHash() {
        EmailVerification plaintext = create();
        ReflectionTestUtils.setField(plaintext, "code", "123456");
        EmailVerification missingHash = create();
        ReflectionTestUtils.setField(missingHash, "codeHash", null);

        assertThat(plaintext.isLegacyFormat()).isTrue();
        assertThat(missingHash.isLegacyFormat()).isTrue();
    }

    @Test
    @DisplayName("최초 생성 시 인증 ID와 발송·시도 횟수를 초기화한다")
    void create_initializesVerification() {
        EmailVerification verification = create();

        assertThat(verification.getVerificationId()).isNotBlank();
        assertThat(verification.dailySendCountAsOf(NOW.toLocalDate())).isEqualTo(1);
        assertThat(verification.getAttemptCount()).isZero();
        assertThat(verification.isVerified()).isFalse();
        assertThat(verification.getConsumedAt()).isNull();
    }

    @Test
    @DisplayName("같은 날 재발송하면 인증 ID를 회전하고 당일 발송 횟수를 누적한다")
    void refresh_sameDay_rotatesIdAndIncrementsDailyCount() {
        EmailVerification verification = create();
        String previousVerificationId = verification.getVerificationId();
        String nextVerificationId = UUID.randomUUID().toString();

        verification.refresh(
                nextVerificationId, "b".repeat(64), NOW.plusMinutes(15), NOW.plusMinutes(5));

        assertThat(verification.getVerificationId()).isEqualTo(nextVerificationId);
        assertThat(verification.getVerificationId()).isNotEqualTo(previousVerificationId);
        assertThat(verification.getDailySendCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("마지막 발송이 다른 날이면 당일 발송 횟수는 0으로 계산된다")
    void dailySendCountAsOf_differentDay_returnsZero() {
        EmailVerification verification = create();

        assertThat(verification.dailySendCountAsOf(NOW.toLocalDate().plusDays(1))).isZero();
    }

    @Test
    @DisplayName("재발송하면 인증 완료·소비 상태와 시도 횟수를 초기화한다")
    void refresh_resetsAttemptVerifiedAndConsumed() {
        EmailVerification verification = create();
        verification.verify(NOW.plusMinutes(1));
        verification.consume(NOW.plusMinutes(2));
        verification.increaseAttempt();

        verification.refresh(
                UUID.randomUUID().toString(),
                "b".repeat(64),
                NOW.plusMinutes(15),
                NOW.plusMinutes(5));

        assertThat(verification.isVerified()).isFalse();
        assertThat(verification.getVerifiedAt()).isNull();
        assertThat(verification.getConsumedAt()).isNull();
        assertThat(verification.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("인증 결과는 30분 직전까지 유효하고 정확히 30분이면 만료된다")
    void isVerifiedResultExpired_boundary() {
        EmailVerification verification = create();
        verification.verify(NOW);

        assertThat(verification.isVerifiedResultExpired(NOW.plusMinutes(30).minusNanos(1), 30))
                .isFalse();
        assertThat(verification.isVerifiedResultExpired(NOW.plusMinutes(30), 30)).isTrue();
    }

    @Test
    @DisplayName("소비 시각을 한 번 기록한다")
    void consume_recordsFirstConsumption() {
        EmailVerification verification = create();

        verification.consume(NOW.plusMinutes(1));
        verification.consume(NOW.plusMinutes(2));

        assertThat(verification.getConsumedAt()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    @DisplayName("쿨다운 시간 이내면 재발송이 막히고 경계부터 허용된다")
    void isWithinResendCooldown_boundary() {
        EmailVerification verification = create();

        assertThat(verification.isWithinResendCooldown(NOW.plusMinutes(2), 3)).isTrue();
        assertThat(verification.isWithinResendCooldown(NOW.plusMinutes(3), 3)).isFalse();
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
