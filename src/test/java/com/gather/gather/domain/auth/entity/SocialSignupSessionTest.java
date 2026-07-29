package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialSignupSessionTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 30, 12, 0);
    private static final LocalDateTime EXPIRES_AT = CREATED_AT.plusMinutes(15);

    @Test
    @DisplayName("가입 세션은 identity key version과 암호문을 보존한 PENDING 상태로 생성된다")
    void create_initializesPendingSession() {
        SocialSignupSession session = session();

        assertThat(session.getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);
        assertThat(session.getProviderUserKeyVersion()).isEqualTo(3);
        assertThat(session.getEncryptionKeyVersion()).isEqualTo(4);
        assertThat(session.getConsumedAt()).isNull();
        assertThat(session.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("만료 시각과 정확히 같아지는 순간부터 만료다")
    void isExpiredAt_usesExclusiveBoundary() {
        SocialSignupSession session = session();

        assertThat(session.isExpiredAt(EXPIRES_AT.minusNanos(1))).isFalse();
        assertThat(session.isExpiredAt(EXPIRES_AT)).isTrue();
        assertThat(session.isExpiredAt(EXPIRES_AT.plusNanos(1))).isTrue();
    }

    @Test
    @DisplayName("PENDING 세션을 소비하면 CONSUMED와 consumedAt이 기록된다")
    void consume_pendingSession_marksConsumed() {
        SocialSignupSession session = session();
        LocalDateTime consumedAt = CREATED_AT.plusMinutes(1);

        session.consume(consumedAt);

        assertThat(session.getStatus()).isEqualTo(SocialSignupSessionStatus.CONSUMED);
        assertThat(session.getConsumedAt()).isEqualTo(consumedAt);
        assertThat(session.getCancelledAt()).isNull();
        assertThat(session.getUpdatedAt()).isEqualTo(consumedAt);
    }

    @Test
    @DisplayName("소비된 세션은 다시 소비하거나 취소할 수 없다")
    void consumedSession_rejectsReuseAndCancellation() {
        SocialSignupSession session = session();
        session.consume(CREATED_AT.plusMinutes(1));

        assertThatThrownBy(() -> session.consume(CREATED_AT.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> session.cancel(CREATED_AT.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("PENDING 세션 취소는 CANCELLED로 전이하고 반복 취소는 멱등이다")
    void cancel_pendingSession_isIdempotent() {
        SocialSignupSession session = session();
        LocalDateTime cancelledAt = CREATED_AT.plusMinutes(1);

        session.cancel(cancelledAt);
        session.cancel(CREATED_AT.plusMinutes(2));

        assertThat(session.getStatus()).isEqualTo(SocialSignupSessionStatus.CANCELLED);
        assertThat(session.getCancelledAt()).isEqualTo(cancelledAt);
        assertThat(session.getConsumedAt()).isNull();
        assertThat(session.getUpdatedAt()).isEqualTo(cancelledAt);
        assertThatThrownBy(() -> session.consume(CREATED_AT.plusMinutes(3)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("만료된 PENDING 세션은 소비할 수 없다")
    void consume_expiredPendingSession_isRejected() {
        SocialSignupSession session = session();

        assertThatThrownBy(() -> session.consume(EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getStatus()).isEqualTo(SocialSignupSessionStatus.PENDING);
    }

    @Test
    @DisplayName("생성 시각보다 이른 소비 또는 취소 시각은 거부한다")
    void transition_beforeCreatedAt_isRejected() {
        assertThatThrownBy(() -> session().consume(CREATED_AT.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> session().cancel(CREATED_AT.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("provider user key는 lowercase SHA-256 hex 64자여야 한다")
    void create_rejectsInvalidProviderUserKey() {
        assertThatThrownBy(() -> sessionWithIdentity("b".repeat(63), "ciphertext"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sessionWithIdentity("G".repeat(64), "ciphertext"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("암호문은 DB 컬럼 길이 512자를 초과할 수 없다")
    void create_rejectsTooLongCiphertext() {
        assertThatThrownBy(() -> sessionWithIdentity("b".repeat(64), "c".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("필수 값과 key version 생성 계약을 검증한다")
    void create_rejectsMissingValuesAndInvalidKeyVersions() {
        assertThatThrownBy(
                        () ->
                                SocialSignupSession.create(
                                        null,
                                        SocialProvider.KAKAO,
                                        "b".repeat(64),
                                        3,
                                        new EncryptedProviderUserId("ciphertext", 4),
                                        EXPIRES_AT,
                                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                SocialSignupSession.create(
                                        "a".repeat(64),
                                        null,
                                        "b".repeat(64),
                                        3,
                                        new EncryptedProviderUserId("ciphertext", 4),
                                        EXPIRES_AT,
                                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                SocialSignupSession.create(
                                        "a".repeat(64),
                                        SocialProvider.KAKAO,
                                        "b".repeat(64),
                                        0,
                                        new EncryptedProviderUserId("ciphertext", 4),
                                        EXPIRES_AT,
                                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedProviderUserId("ciphertext", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SocialSignupSession session() {
        return sessionWithIdentity("b".repeat(64), "ciphertext");
    }

    private SocialSignupSession sessionWithIdentity(String providerUserKey, String ciphertext) {
        return SocialSignupSession.create(
                "a".repeat(64),
                SocialProvider.KAKAO,
                providerUserKey,
                3,
                new EncryptedProviderUserId(ciphertext, 4),
                EXPIRES_AT,
                CREATED_AT);
    }
}
