package com.gather.gather.domain.auth.kakao.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.kakao.dto.KakaoLoginRequest;
import com.gather.gather.domain.auth.kakao.dto.KakaoSignupRequest;
import com.gather.gather.domain.auth.kakao.service.KakaoAuthService;
import com.gather.gather.domain.auth.kakao.service.KakaoLoginResult;
import com.gather.gather.domain.auth.service.RefreshTokenCookieProvider;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.hamcrest.Matcher;
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

@WebMvcTest(KakaoAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class KakaoAuthControllerTest {

    private static final String REFRESH_COOKIE_NAME = "gather_refresh_token";
    private static final String SIGNUP_TOKEN_HEADER = "X-Signup-Token";
    private static final String REDIRECT_URI = "https://gathernow.kr/login/kakao/callback";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private KakaoAuthService kakaoAuthService;
    @MockitoBean private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Test
    @DisplayName("기존 회원 로그인은 LOGIN_COMPLETED와 Access Token, Refresh Token 쿠키를 내려준다")
    void login_whenLoginCompleted_returnsAccessTokenAndRefreshCookie() throws Exception {
        when(kakaoAuthService.login(any(KakaoLoginRequest.class)))
                .thenReturn(
                        KakaoLoginResult.loginCompleted(
                                new TokenIssueResult("access-token", "refresh-token")));
        when(refreshTokenCookieProvider.create("refresh-token"))
                .thenReturn(refreshCookie("refresh-token"));

        mockMvc.perform(loginRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.signupStatus").value("LOGIN_COMPLETED"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.signupToken").doesNotExist())
                .andExpect(jsonPath("$.data.profile").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, refreshCookieMatcher()));
    }

    @Test
    @DisplayName("신규 회원은 200과 함께 가입 토큰·닉네임만 받고 로그인 쿠키는 받지 않는다")
    void login_whenAdditionalInfoRequired_returnsSignupTokenWithoutCookie() throws Exception {
        when(kakaoAuthService.login(any(KakaoLoginRequest.class)))
                .thenReturn(KakaoLoginResult.additionalInfoRequired("signup-token", "동현"));

        mockMvc.perform(loginRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.signupStatus").value("ADDITIONAL_INFO_REQUIRED"))
                .andExpect(jsonPath("$.data.signupToken").value("signup-token"))
                .andExpect(jsonPath("$.data.profile.nickname").value("동현"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.tokenType").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(refreshTokenCookieProvider);
    }

    @Test
    @DisplayName("카카오 닉네임이 없으면 profile.nickname은 null로 내려간다")
    void login_whenNicknameMissing_returnsNullNickname() throws Exception {
        when(kakaoAuthService.login(any(KakaoLoginRequest.class)))
                .thenReturn(KakaoLoginResult.additionalInfoRequired("signup-token", null));

        mockMvc.perform(loginRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile").exists())
                .andExpect(jsonPath("$.data.profile.nickname").doesNotExist());
    }

    @Test
    @DisplayName("인가 코드가 무효하면 400을 반환한다")
    void login_whenAuthorizationCodeInvalid_returnsBadRequest() throws Exception {
        when(kakaoAuthService.login(any(KakaoLoginRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(loginRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("정지된 기존 카카오 회원 로그인은 403 SUSPENDED_USER를 반환한다")
    void login_whenSuspendedMember_returnsForbidden() throws Exception {
        when(kakaoAuthService.login(any(KakaoLoginRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.SUSPENDED_USER));

        mockMvc.perform(loginRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUSPENDED_USER"));
    }

    @Test
    @DisplayName("탈퇴한 기존 카카오 회원 로그인은 403 WITHDRAWN_USER를 반환한다")
    void login_whenWithdrawnMember_returnsForbidden() throws Exception {
        when(kakaoAuthService.login(any(KakaoLoginRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.WITHDRAWN_USER));

        mockMvc.perform(loginRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WITHDRAWN_USER"));
    }

    @Test
    @DisplayName("요청 본문에 authorizationCode가 없으면 서비스를 호출하지 않고 400을 반환한다")
    void login_withoutAuthorizationCode_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/kakao/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "redirectUri": "https://gathernow.kr/login/kakao/callback"
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(kakaoAuthService);
    }

    @Test
    @DisplayName("추가정보 가입은 201과 Access Token, Refresh Token 쿠키를 내려준다")
    void signup_returnsCreatedWithTokens() throws Exception {
        when(kakaoAuthService.signup(eq("signup-token"), any(KakaoSignupRequest.class)))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));
        when(refreshTokenCookieProvider.create("refresh-token"))
                .thenReturn(refreshCookie("refresh-token"));

        mockMvc.perform(signupRequest().header(SIGNUP_TOKEN_HEADER, "signup-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, refreshCookieMatcher()));
    }

    @Test
    @DisplayName("가입 토큰 헤더가 없으면 500이 아니라 SIGNUP_TOKEN_INVALID(401)로 처리한다")
    void signup_withoutSignupTokenHeader_returnsSignupTokenInvalid() throws Exception {
        when(kakaoAuthService.signup(isNull(), any(KakaoSignupRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID));

        mockMvc.perform(signupRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SIGNUP_TOKEN_INVALID"));

        verify(kakaoAuthService).signup(isNull(), any(KakaoSignupRequest.class));
    }

    @Test
    @DisplayName("가입 토큰이 만료되면 401 SIGNUP_TOKEN_EXPIRED를 반환한다")
    void signup_withExpiredSignupToken_returnsSignupTokenExpired() throws Exception {
        when(kakaoAuthService.signup(eq("expired-token"), any(KakaoSignupRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.SIGNUP_TOKEN_EXPIRED));

        mockMvc.perform(signupRequest().header(SIGNUP_TOKEN_HEADER, "expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SIGNUP_TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("이미 가입된 카카오 계정이면 409 ALREADY_REGISTERED를 반환한다")
    void signup_whenAlreadyRegistered_returnsConflict() throws Exception {
        when(kakaoAuthService.signup(eq("signup-token"), any(KakaoSignupRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.ALREADY_REGISTERED));

        mockMvc.perform(signupRequest().header(SIGNUP_TOKEN_HEADER, "signup-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("가입 요청에 이메일·비밀번호를 넣어도 무시하고 나머지 필드로 검증한다")
    void signup_ignoresEmailAndPasswordFields() throws Exception {
        when(kakaoAuthService.signup(eq("signup-token"), any(KakaoSignupRequest.class)))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));
        when(refreshTokenCookieProvider.create("refresh-token"))
                .thenReturn(refreshCookie("refresh-token"));

        mockMvc.perform(
                        post("/api/v1/auth/kakao/signup")
                                .header(SIGNUP_TOKEN_HEADER, "signup-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "2002-03-15",
                                          "gender": "MALE",
                                          "phoneNumber": "01012345678",
                                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
                                          "email": "test@example.com",
                                          "password": "password123!",
                                          "nickname": "길동",
                                          "introduction": null,
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"],
                                          "serviceTermsAgreed": true,
                                          "privacyPolicyAgreed": true,
                                          "marketingAgreed": false
                                        }
                                        """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("잘못된 관심 카테고리 enum 문자열은 400으로 변환한다")
    void signup_withInvalidInterestCategory_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/kakao/signup")
                                .header(SIGNUP_TOKEN_HEADER, "signup-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "2002-03-15",
                                          "gender": "MALE",
                                          "phoneNumber": "01012345678",
                                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
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
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(kakaoAuthService);
    }

    @Test
    @DisplayName("카카오 가입의 휴대폰 인증 ID가 UUID가 아니면 서비스 호출 전에 400으로 막는다")
    void signup_withInvalidPhoneVerificationId_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/kakao/signup")
                                .header(SIGNUP_TOKEN_HEADER, "signup-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "2002-03-15",
                                          "gender": "MALE",
                                          "phoneNumber": "01012345678",
                                          "phoneVerificationId": "not-a-uuid",
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

        verifyNoInteractions(kakaoAuthService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  \"phoneVerificationId\": null,"})
    @DisplayName("카카오 가입의 휴대폰 인증 ID가 누락되거나 null이면 PHONE_VERIFICATION_REQUIRED를 반환한다")
    void signup_withoutPhoneVerificationId_returnsPhoneVerificationRequired(
            String phoneVerificationField) throws Exception {
        when(kakaoAuthService.signup(eq("signup-token"), any(KakaoSignupRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PHONE_VERIFICATION_REQUIRED));

        mockMvc.perform(
                        post("/api/v1/auth/kakao/signup")
                                .header(SIGNUP_TOKEN_HEADER, "signup-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "2002-03-15",
                                          "gender": "MALE",
                                          "phoneNumber": "01012345678",
                                        %s
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

        verify(kakaoAuthService).signup(eq("signup-token"), any(KakaoSignupRequest.class));
    }

    @Test
    @DisplayName("전화번호가 20자를 넘으면 저장 전에 400으로 막는다")
    void signup_withTooLongPhoneNumber_returnsBadRequest() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/kakao/signup")
                                .header(SIGNUP_TOKEN_HEADER, "signup-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "2002-03-15",
                                          "gender": "MALE",
                                          "phoneNumber": "010123456789012345678",
                                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
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
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(kakaoAuthService);
    }

    @Test
    @DisplayName("전화번호가 20자이면 검증을 통과한다")
    void signup_withMaxLengthPhoneNumber_returnsCreated() throws Exception {
        when(kakaoAuthService.signup(eq("signup-token"), any(KakaoSignupRequest.class)))
                .thenReturn(new TokenIssueResult("access-token", "refresh-token"));
        when(refreshTokenCookieProvider.create("refresh-token"))
                .thenReturn(refreshCookie("refresh-token"));

        mockMvc.perform(
                        post("/api/v1/auth/kakao/signup")
                                .header(SIGNUP_TOKEN_HEADER, "signup-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "birthDate": "2002-03-15",
                                          "gender": "MALE",
                                          "phoneNumber": "01012345678901234567",
                                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
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
                .andExpect(jsonPath("$.success").value(true));

        verify(kakaoAuthService).signup(eq("signup-token"), any(KakaoSignupRequest.class));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            loginRequest() {
        return post("/api/v1/auth/kakao/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {
                          "authorizationCode": "auth-code",
                          "redirectUri": "%s"
                        }
                        """
                                .formatted(REDIRECT_URI));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            signupRequest() {
        return post("/api/v1/auth/kakao/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {
                          "name": "홍길동",
                          "birthDate": "2002-03-15",
                          "gender": "MALE",
                          "phoneNumber": "01012345678",
                          "phoneVerificationId": "5c5d5db1-4187-43d0-8580-672307994878",
                          "nickname": "길동",
                          "introduction": null,
                          "activityRegionId": 123,
                          "interestCategories": ["WELFARE"],
                          "serviceTermsAgreed": true,
                          "privacyPolicyAgreed": true,
                          "marketingAgreed": false
                        }
                        """);
    }

    private static ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .path("/api/v1/auth")
                .maxAge(1209600)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
    }

    private static Matcher<String> refreshCookieMatcher() {
        return allOf(
                containsString(REFRESH_COOKIE_NAME + "="),
                containsString("Path=/api/v1/auth"),
                containsString("HttpOnly"),
                containsString("SameSite=Lax"));
    }
}
