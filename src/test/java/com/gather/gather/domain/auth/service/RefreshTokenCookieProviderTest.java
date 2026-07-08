package com.gather.gather.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class RefreshTokenCookieProviderTest {

    @Test
    @DisplayName("Refresh Token 쿠키를 설정값과 일치하게 생성한다")
    void create_returnsRefreshTokenCookie() {
        RefreshTokenCookieProvider provider =
                new RefreshTokenCookieProvider(
                        new RefreshTokenCookieProperties(
                                "gather_refresh_token", false, "Lax", "/api/v1/auth", 1209600));

        ResponseCookie cookie = provider.create("refresh-token");

        assertThat(cookie.getName()).isEqualTo("gather_refresh_token");
        assertThat(cookie.getValue()).isEqualTo("refresh-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(1209600));
    }

    @Test
    @DisplayName("Refresh Token 삭제 쿠키는 발급 쿠키와 같은 속성으로 즉시 만료된다")
    void clear_returnsExpiredRefreshTokenCookie() {
        RefreshTokenCookieProvider provider =
                new RefreshTokenCookieProvider(
                        new RefreshTokenCookieProperties(
                                "gather_refresh_token", true, "Lax", "/api/v1/auth", 1209600));

        ResponseCookie cookie = provider.clear();

        assertThat(cookie.getName()).isEqualTo("gather_refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }
}
