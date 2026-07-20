package com.gather.gather.domain.auth.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.dto.LoginRequest;
import com.gather.gather.domain.auth.service.AuthService;
import com.gather.gather.domain.auth.service.RefreshTokenCookieProvider;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String REFRESH_COOKIE_NAME = "gather_refresh_token";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Test
    @DisplayName("회원가입에서 잘못된 관심 카테고리 enum 문자열은 400으로 변환한다")
    void signup_withInvalidInterestCategory_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "2000-01-01",
                                          "gender": "MALE",
                                          "phoneNumber": "01012345678",
                                          "email": "test@example.com",
                                          "password": "password123!",
                                          "passwordConfirm": "password123!",
                                          "nickname": "길동",
                                          "introduction": null,
                                          "activityRegionId": 123,
                                          "interestCategories": ["INVALID"],
                                          "serviceTermsAgreed": true,
                                          "privacyPolicyAgreed": true,
                                          "marketingAgreed": false
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("로그인 성공 시 Refresh Token은 HttpOnly 쿠키로 내려가고 응답 본문에는 포함되지 않는다")
    void login_setsRefreshTokenCookieAndOmitsRefreshTokenBody() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));
        when(refreshTokenCookieProvider.create("refresh-token"))
                .thenReturn(refreshCookie("refresh-token"));

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "test@example.com",
                                          "password": "password123!"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, refreshCookieMatcher()));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("재발급은 Refresh Token 쿠키를 읽고 새 Refresh Token 쿠키를 내려준다")
    void reissue_readsRefreshTokenCookieAndSetsRotatedCookie() throws Exception {
        when(refreshTokenCookieProvider.cookieName()).thenReturn(REFRESH_COOKIE_NAME);
        when(authService.reissue("old-refresh-token"))
                .thenReturn(new TokenIssueResult("new-access-token", "new-refresh-token"));
        when(refreshTokenCookieProvider.create("new-refresh-token"))
                .thenReturn(refreshCookie("new-refresh-token"));

        mockMvc.perform(
                        post("/api/v1/auth/reissue")
                                .cookie(new Cookie(REFRESH_COOKIE_NAME, "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, refreshCookieMatcher()));

        verify(authService).reissue("old-refresh-token");
    }

    @Test
    @DisplayName("재발급 요청에 Refresh Token 쿠키가 없으면 INVALID_TOKEN을 반환한다")
    void reissue_withoutRefreshTokenCookie_returnsInvalidToken() throws Exception {
        when(refreshTokenCookieProvider.cookieName()).thenReturn(REFRESH_COOKIE_NAME);

        mockMvc.perform(post("/api/v1/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("로그아웃은 Refresh Token 쿠키를 읽어 폐기하고 삭제 쿠키를 내려준다")
    void logout_revokesRefreshTokenAndClearsCookie() throws Exception {
        when(refreshTokenCookieProvider.cookieName()).thenReturn(REFRESH_COOKIE_NAME);
        when(refreshTokenCookieProvider.clear()).thenReturn(clearCookie());

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .cookie(new Cookie(REFRESH_COOKIE_NAME, "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(
                        header().string(
                                        HttpHeaders.SET_COOKIE,
                                        allOf(
                                                containsString(REFRESH_COOKIE_NAME + "="),
                                                containsString("Path=/api/v1/auth"),
                                                containsString("Max-Age=0"),
                                                containsString("HttpOnly"),
                                                containsString("SameSite=Lax"))));

        verify(authService).logout("refresh-token");
    }

    @Test
    @DisplayName("로그아웃에서 Refresh Token이 유효하지 않아도 삭제 쿠키를 내려준다")
    void logout_withInvalidRefreshToken_stillClearsCookie() throws Exception {
        when(refreshTokenCookieProvider.cookieName()).thenReturn(REFRESH_COOKIE_NAME);
        when(refreshTokenCookieProvider.clear()).thenReturn(clearCookie());
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.INVALID_TOKEN))
                .when(authService)
                .logout("invalid-refresh-token");

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .cookie(new Cookie(REFRESH_COOKIE_NAME, "invalid-refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, clearCookieMatcher()));

        verify(authService).logout("invalid-refresh-token");
    }

    private static ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .path("/api/v1/auth")
                .maxAge(1209600)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
    }

    private static ResponseCookie clearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .path("/api/v1/auth")
                .maxAge(0)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
    }

    private static org.hamcrest.Matcher<String> refreshCookieMatcher() {
        return allOf(
                containsString(REFRESH_COOKIE_NAME + "="),
                containsString("Path=/api/v1/auth"),
                containsString("Max-Age=1209600"),
                containsString("HttpOnly"),
                containsString("SameSite=Lax"),
                not(containsString("Secure")));
    }

    private static org.hamcrest.Matcher<String> clearCookieMatcher() {
        return allOf(
                containsString(REFRESH_COOKIE_NAME + "="),
                containsString("Path=/api/v1/auth"),
                containsString("Max-Age=0"),
                containsString("HttpOnly"),
                containsString("SameSite=Lax"));
    }
}
