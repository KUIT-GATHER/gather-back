package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.domain.auth.service.PasswordResetAuthorityResult.Outcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordResetAuthorityResultTest {

    private static final String RAW_TOKEN = "A".repeat(43);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 8, 18, 4, 10, 0);

    @Test
    @DisplayName("EMAIL 결과는 raw token과 만료 시각을 가진다")
    void email_carriesRawTokenAndExpiresAt() {
        PasswordResetAuthorityResult result =
                PasswordResetAuthorityResult.email(RAW_TOKEN, EXPIRES_AT);

        assertThat(result.outcome()).isEqualTo(Outcome.EMAIL);
        assertThat(result.rawToken()).isEqualTo(RAW_TOKEN);
        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("KAKAO와 ACCOUNT_NOT_FOUND 결과는 raw token을 갖지 않는다")
    void kakaoAndAccountNotFound_haveNoRawToken() {
        assertThat(PasswordResetAuthorityResult.kakao().rawToken()).isNull();
        assertThat(PasswordResetAuthorityResult.accountNotFound().rawToken()).isNull();
    }

    @Test
    @DisplayName("결과 종류가 없으면 만들 수 없다")
    void constructor_rejectsNullOutcome() {
        assertThatThrownBy(() -> new PasswordResetAuthorityResult(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EMAIL 결과에 raw token이 없으면 만들 수 없다")
    void constructor_rejectsEmailWithoutRawToken() {
        assertThatThrownBy(() -> new PasswordResetAuthorityResult(Outcome.EMAIL, null, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordResetAuthorityResult(Outcome.EMAIL, " ", EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EMAIL 결과에 만료 시각이 없으면 만들 수 없다")
    void constructor_rejectsEmailWithoutExpiresAt() {
        assertThatThrownBy(() -> new PasswordResetAuthorityResult(Outcome.EMAIL, RAW_TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EMAIL 외 결과에 raw token을 담을 수 없다")
    void constructor_rejectsRawTokenForNonEmailOutcome() {
        assertThatThrownBy(() -> new PasswordResetAuthorityResult(Outcome.KAKAO, RAW_TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new PasswordResetAuthorityResult(
                                        Outcome.ACCOUNT_NOT_FOUND, RAW_TOKEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EMAIL 외 결과에 만료 시각을 담을 수 없다")
    void constructor_rejectsExpiresAtForNonEmailOutcome() {
        assertThatThrownBy(() -> new PasswordResetAuthorityResult(Outcome.KAKAO, null, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new PasswordResetAuthorityResult(
                                        Outcome.ACCOUNT_NOT_FOUND, null, EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toString에 raw token 평문이 노출되지 않는다")
    void toString_redactsRawToken() {
        assertThat(PasswordResetAuthorityResult.email(RAW_TOKEN, EXPIRES_AT).toString())
                .doesNotContain(RAW_TOKEN)
                .contains("rawToken=***");
        assertThat(PasswordResetAuthorityResult.kakao().toString()).contains("rawToken=null");
    }
}
