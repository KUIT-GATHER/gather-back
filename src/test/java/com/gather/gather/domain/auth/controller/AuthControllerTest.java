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

import com.gather.gather.domain.auth.dto.EmailVerificationConfirmRequest;
import com.gather.gather.domain.auth.dto.EmailVerificationConfirmResponse;
import com.gather.gather.domain.auth.dto.LoginRequest;
import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.dto.SignupResponse;
import com.gather.gather.domain.auth.service.AuthService;
import com.gather.gather.domain.auth.service.RefreshTokenCookieProvider;
import com.gather.gather.domain.auth.service.SignupResult;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
                                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
                                          "email": "test@example.com",
                                          "emailVerificationId": "98fa88ef-bbeb-4928-a202-7885197b3774",
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
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("회원가입의 휴대폰 인증 ID가 UUID가 아니면 서비스 호출 전에 400으로 막는다")
    void signup_withInvalidPhoneVerificationId_returnsBadRequest() throws Exception {
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
                                          "phoneVerificationId": "not-a-uuid",
                                          "email": "test@example.com",
                                          "emailVerificationId": "98fa88ef-bbeb-4928-a202-7885197b3774",
                                          "password": "password123!",
                                          "passwordConfirm": "password123!",
                                          "nickname": "길동",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"],
                                          "serviceTermsAgreed": true,
                                          "privacyPolicyAgreed": true,
                                          "marketingAgreed": false
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  \"phoneVerificationId\": null,"})
    @DisplayName("회원가입의 휴대폰 인증 ID가 누락되거나 null이면 PHONE_VERIFICATION_REQUIRED를 반환한다")
    void signup_withoutPhoneVerificationId_returnsPhoneVerificationRequired(
            String phoneVerificationField) throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED));

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
                                        %s
                                          "email": "test@example.com",
                                          "emailVerificationId": "98fa88ef-bbeb-4928-a202-7885197b3774",
                                          "password": "password123!",
                                          "passwordConfirm": "password123!",
                                          "nickname": "길동",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"],
                                          "serviceTermsAgreed": true,
                                          "privacyPolicyAgreed": true,
                                          "marketingAgreed": false
                                        }
                                        """
                                                .formatted(phoneVerificationField)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PHONE_VERIFICATION_REQUIRED"));

        verify(authService).signup(any(SignupRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  \"emailVerificationId\": null,"})
    @DisplayName("회원가입의 이메일 인증 ID가 누락되거나 null이면 EMAIL_VERIFICATION_REQUIRED를 반환한다")
    void signup_withoutEmailVerificationId_returnsEmailVerificationRequired(
            String emailVerificationField) throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED));

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
                                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
                                          "email": "test@example.com",
                                        %s
                                          "password": "password123!",
                                          "passwordConfirm": "password123!",
                                          "nickname": "길동",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"],
                                          "serviceTermsAgreed": true,
                                          "privacyPolicyAgreed": true,
                                          "marketingAgreed": false
                                        }
                                        """
                                                .formatted(emailVerificationField)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMAIL_VERIFICATION_REQUIRED"));

        verify(authService).signup(any(SignupRequest.class));
    }

    @Test
    @DisplayName("회원가입의 이메일 인증 ID가 UUID가 아니면 서비스 호출 전에 400으로 막는다")
    void signup_withInvalidEmailVerificationId_returnsBadRequest() throws Exception {
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
                                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
                                          "email": "test@example.com",
                                          "emailVerificationId": "not-a-uuid",
                                          "password": "password123!",
                                          "passwordConfirm": "password123!",
                                          "nickname": "길동",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"],
                                          "serviceTermsAgreed": true,
                                          "privacyPolicyAgreed": true,
                                          "marketingAgreed": false
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("이메일 인증 확인 성공 응답에 회원가입용 인증 ID를 포함한다")
    void confirmEmailVerification_returnsVerificationId() throws Exception {
        UUID verificationId = UUID.fromString("98fa88ef-bbeb-4928-a202-7885197b3774");
        when(authService.confirmEmailVerificationCode(any(EmailVerificationConfirmRequest.class)))
                .thenReturn(
                        new EmailVerificationConfirmResponse(
                                "test@example.com",
                                true,
                                LocalDateTime.of(2026, 8, 10, 10, 0),
                                verificationId));

        mockMvc.perform(
                        post("/api/v1/auth/email-verifications/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "email": "test@example.com",
                                          "code": "123456"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerificationId").value(verificationId.toString()));
    }

    @Test
    @DisplayName("회원가입에서 전화번호가 20자를 넘으면 저장 전에 400으로 막는다")
    void signup_withTooLongPhoneNumber_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "2000-01-01",
                                          "gender": "MALE",
                                          "phoneNumber": "010123456789012345678",
                                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
                                          "email": "test@example.com",
                                          "emailVerificationId": "98fa88ef-bbeb-4928-a202-7885197b3774",
                                          "password": "password123!",
                                          "passwordConfirm": "password123!",
                                          "nickname": "길동",
                                          "introduction": null,
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"],
                                          "serviceTermsAgreed": true,
                                          "privacyPolicyAgreed": true,
                                          "marketingAgreed": false
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("회원가입에서 전화번호가 20자이면 검증을 통과한다")
    void signup_withMaxLengthPhoneNumber_returnsCreated() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenReturn(
                        new SignupResult(
                                new SignupResponse(
                                        1L,
                                        "test@example.com",
                                        "홍길동",
                                        "길동",
                                        "access-token",
                                        "Bearer"),
                                "refresh-token"));
        when(refreshTokenCookieProvider.create("refresh-token"))
                .thenReturn(refreshCookie("refresh-token"));

        mockMvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "2000-01-01",
                                          "gender": "MALE",
                                          "phoneNumber": "01012345678901234567",
                                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
                                          "email": "test@example.com",
                                          "emailVerificationId": "98fa88ef-bbeb-4928-a202-7885197b3774",
                                          "password": "password123!",
                                          "passwordConfirm": "password123!",
                                          "nickname": "길동",
                                          "introduction": null,
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"],
                                          "serviceTermsAgreed": true,
                                          "privacyPolicyAgreed": true,
                                          "marketingAgreed": false
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.nickname").value("길동"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, refreshCookieMatcher()));

        verify(authService).signup(any(SignupRequest.class));
    }

    @Test
    @DisplayName("제거된 전화번호 중복확인 API는 더 이상 매핑되지 않는다")
    void phoneNumberAvailability_isRemoved() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/phone-numbers/availability")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phoneNumber\":\"01012345678\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

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
    @DisplayName("세션 복원은 Refresh Token 쿠키가 없으면 쿠키 변경 없이 anonymous를 반환한다")
    void restoreSession_withoutRefreshTokenCookie_returnsAnonymousWithoutCookieChange()
            throws Exception {
        when(refreshTokenCookieProvider.cookieName()).thenReturn(REFRESH_COOKIE_NAME);

        mockMvc.perform(post("/api/v1/auth/session/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.accessToken").value((Object) null))
                .andExpect(jsonPath("$.data.tokenType").value((Object) null))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("세션 복원 성공은 Access Token과 rotation된 Refresh Token 쿠키를 반환한다")
    void restoreSession_withValidRefreshToken_returnsTokens() throws Exception {
        when(refreshTokenCookieProvider.cookieName()).thenReturn(REFRESH_COOKIE_NAME);
        when(authService.restoreSession("old-refresh-token"))
                .thenReturn(
                        Optional.of(new TokenIssueResult("new-access-token", "new-refresh-token")));
        when(refreshTokenCookieProvider.create("new-refresh-token"))
                .thenReturn(refreshCookie("new-refresh-token"));

        mockMvc.perform(
                        post("/api/v1/auth/session/restore")
                                .cookie(new Cookie(REFRESH_COOKIE_NAME, "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, refreshCookieMatcher()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"invalid-refresh-token", "expired-refresh-token", "revoked-refresh-token"})
    @DisplayName("복원할 수 없는 Refresh Token은 쿠키 변경 없이 anonymous를 반환한다")
    void restoreSession_withUnavailableRefreshToken_returnsAnonymousWithoutCookieChange(
            String refreshToken) throws Exception {
        when(refreshTokenCookieProvider.cookieName()).thenReturn(REFRESH_COOKIE_NAME);
        when(authService.restoreSession(refreshToken)).thenReturn(Optional.empty());

        mockMvc.perform(
                        post("/api/v1/auth/session/restore")
                                .cookie(new Cookie(REFRESH_COOKIE_NAME, refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("계정 상태로 차단된 세션 복원은 기존 403을 유지하고 쿠키를 변경하지 않는다")
    void restoreSession_withBlockedUser_returnsForbiddenWithoutCookieChange() throws Exception {
        when(refreshTokenCookieProvider.cookieName()).thenReturn(REFRESH_COOKIE_NAME);
        when(authService.restoreSession("blocked-user-refresh-token"))
                .thenThrow(new BusinessException(ErrorCode.SUSPENDED_USER));

        mockMvc.perform(
                        post("/api/v1/auth/session/restore")
                                .cookie(
                                        new Cookie(
                                                REFRESH_COOKIE_NAME, "blocked-user-refresh-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUSPENDED_USER"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("예상하지 못한 세션 복원 오류는 500을 유지하고 쿠키를 변경하지 않는다")
    void restoreSession_withServerFailure_returnsInternalServerErrorWithoutCookieChange()
            throws Exception {
        when(refreshTokenCookieProvider.cookieName()).thenReturn(REFRESH_COOKIE_NAME);
        when(authService.restoreSession("refresh-token"))
                .thenThrow(new IllegalStateException("database unavailable"));

        mockMvc.perform(
                        post("/api/v1/auth/session/restore")
                                .cookie(new Cookie(REFRESH_COOKIE_NAME, "refresh-token")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
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
