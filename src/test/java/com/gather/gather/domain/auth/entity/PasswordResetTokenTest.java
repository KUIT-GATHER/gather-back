package com.gather.gather.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.posting.entity.PostingCategory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordResetTokenTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 17, 12, 0);
    private static final String VALID_HASH = "a".repeat(64);

    @Test
    @DisplayName("유효한 hash와 만료 시각으로 토큰을 발급한다")
    void issue_createsToken() {
        User user = emailUser();

        PasswordResetToken token =
                PasswordResetToken.issue(user, VALID_HASH, NOW.plusMinutes(10), NOW);

        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.getTokenHash()).isEqualTo(VALID_HASH);
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(token.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("만료 시각 도달 시점부터 만료로 판정한다")
    void isExpired_atExpiresAtIsExpired() {
        PasswordResetToken token =
                PasswordResetToken.issue(emailUser(), VALID_HASH, NOW.plusMinutes(10), NOW);

        assertThat(token.isExpired(NOW.plusMinutes(10).minusNanos(1_000))).isFalse();
        assertThat(token.isExpired(NOW.plusMinutes(10))).isTrue();
        assertThat(token.isExpired(NOW.plusMinutes(11))).isTrue();
    }

    @Test
    @DisplayName("사용자가 없으면 발급을 거부한다")
    void issue_rejectsNullUser() {
        assertThatThrownBy(
                        () -> PasswordResetToken.issue(null, VALID_HASH, NOW.plusMinutes(10), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("64자 소문자 hex가 아닌 hash는 거부한다")
    void issue_rejectsInvalidHash() {
        User user = emailUser();

        assertThatThrownBy(() -> PasswordResetToken.issue(user, null, NOW.plusMinutes(10), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordResetToken.issue(user, "", NOW.plusMinutes(10), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                PasswordResetToken.issue(
                                        user, "a".repeat(63), NOW.plusMinutes(10), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                PasswordResetToken.issue(
                                        user, "a".repeat(65), NOW.plusMinutes(10), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                PasswordResetToken.issue(
                                        user, "A".repeat(64), NOW.plusMinutes(10), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                PasswordResetToken.issue(
                                        user, "g".repeat(64), NOW.plusMinutes(10), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성 시각이 없으면 발급을 거부한다")
    void issue_rejectsNullCreatedAt() {
        User user = emailUser();

        assertThatThrownBy(
                        () -> PasswordResetToken.issue(user, VALID_HASH, NOW.plusMinutes(10), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("만료 시각이 생성 시각보다 늦지 않으면 발급을 거부한다")
    void issue_rejectsExpiresAtNotAfterCreatedAt() {
        User user = emailUser();

        assertThatThrownBy(() -> PasswordResetToken.issue(user, VALID_HASH, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PasswordResetToken.issue(user, VALID_HASH, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> PasswordResetToken.issue(user, VALID_HASH, NOW.minusMinutes(1), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User emailUser() {
        return User.create(
                "홍길동",
                LocalDate.of(2002, 3, 15),
                Gender.MALE,
                "01090000000",
                "reset-token@example.com",
                "encoded-password",
                "재설정",
                null,
                true,
                true,
                false,
                null,
                List.of(PostingCategory.WELFARE));
    }
}
