package com.gather.gather.domain.user.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.dto.PasswordChangeRequest;
import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.service.AccountTerminationOutcome;
import com.gather.gather.domain.auth.service.AccountTerminationResult;
import com.gather.gather.domain.auth.service.AccountTerminationService;
import com.gather.gather.domain.auth.service.PasswordChangeService;
import com.gather.gather.domain.auth.service.RefreshTokenCookieProvider;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.domain.user.dto.UserProfileResponse;
import com.gather.gather.domain.user.service.UserProfileService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserProfileService userProfileService;
    @MockitoBean private AccountTerminationService accountTerminationService;
    @MockitoBean private PasswordChangeService passwordChangeService;
    @MockitoBean private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(42L, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me maps COMPLETED to 200 with UTC time and clear cookie")
    void terminateMyAccount_returns200_whenCompleted() throws Exception {
        when(accountTerminationService.terminate(42L, WithdrawalReason.SELF))
                .thenReturn(
                        new AccountTerminationResult(
                                AccountTerminationOutcome.COMPLETED,
                                LocalDateTime.of(2026, 8, 1, 14, 0)));
        when(refreshTokenCookieProvider.clear()).thenReturn(clearCookie());

        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.occurredAt").value("2026-08-01T14:00:00Z"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                .string(
                                        org.springframework.http.HttpHeaders.SET_COOKIE,
                                        allOf(
                                                containsString("gather_refresh_token="),
                                                containsString("Max-Age=0"),
                                                containsString("Path=/api/v1/auth"),
                                                containsString("HttpOnly"),
                                                containsString("SameSite=Lax"))));

        verify(accountTerminationService).terminate(42L, WithdrawalReason.SELF);
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me maps ACCEPTED to 202 without a request body")
    void terminateMyAccount_returns202_whenAccepted() throws Exception {
        when(accountTerminationService.terminate(42L, WithdrawalReason.SELF))
                .thenReturn(
                        new AccountTerminationResult(
                                AccountTerminationOutcome.ACCEPTED,
                                LocalDateTime.of(2026, 8, 1, 14, 0)));
        when(refreshTokenCookieProvider.clear()).thenReturn(clearCookie());

        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.occurredAt").value("2026-08-01T14:00:00Z"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                .string(
                                        org.springframework.http.HttpHeaders.SET_COOKIE,
                                        allOf(
                                                containsString("gather_refresh_token="),
                                                containsString("Max-Age=0"),
                                                containsString("Path=/api/v1/auth"),
                                                containsString("HttpOnly"),
                                                containsString("SameSite=Lax"))));

        verify(accountTerminationService).terminate(42L, WithdrawalReason.SELF);
    }

    @Test
    @DisplayName("GET /api/v1/users/me returns the current user's profile")
    void getMyProfile_returns200WithProfile() throws Exception {
        when(userProfileService.getMyProfile()).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("길동"))
                .andExpect(jsonPath("$.data.loginType").value("EMAIL"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me exposes KAKAO loginType for Kakao-only accounts")
    void getMyProfile_returnsKakaoLoginType() throws Exception {
        when(userProfileService.getMyProfile()).thenReturn(sampleProfile(AccountLoginType.KAKAO));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginType").value("KAKAO"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me updates the profile and returns 200")
    void updateMyProfile_returns200_whenValid() throws Exception {
        when(userProfileService.updateMyProfile(any())).thenReturn(sampleProfile());

        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "nickname": "길동",
                                          "introduction": "소개글",
                                          "birthDate": "2000-01-01",
                                          "gender": "MALE",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"]
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("길동"))
                .andExpect(jsonPath("$.data.loginType").value("EMAIL"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me returns 409 when the nickname is already taken")
    void updateMyProfile_returns409_whenNicknameDuplicate() throws Exception {
        when(userProfileService.updateMyProfile(any()))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_NICKNAME));

        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "홍길동",
                                          "nickname": "이미있는닉네임",
                                          "introduction": null,
                                          "birthDate": "2000-01-01",
                                          "gender": "MALE",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"]
                                        }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_NICKNAME"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me returns 400 when required fields are missing")
    void updateMyProfile_returns400_whenValidationFails() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "name": "",
                                          "nickname": "길동",
                                          "birthDate": "2000-01-01",
                                          "gender": "MALE",
                                          "activityRegionId": 123,
                                          "interestCategories": ["WELFARE"]
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password returns 200 with null data and a clear cookie")
    void changeMyPassword_returns200_whenValid() throws Exception {
        when(refreshTokenCookieProvider.clear()).thenReturn(clearCookie());

        mockMvc.perform(passwordChangeRequest("oldpass123", "newpass123", "newpass123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                .string(
                                        org.springframework.http.HttpHeaders.SET_COOKIE,
                                        allOf(
                                                containsString("gather_refresh_token="),
                                                containsString("Max-Age=0"),
                                                containsString("Path=/api/v1/auth"),
                                                containsString("HttpOnly"),
                                                containsString("SameSite=Lax"))));

        verify(passwordChangeService)
                .changePassword(
                        42L, new PasswordChangeRequest("oldpass123", "newpass123", "newpass123"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password returns 400 when the current password is wrong")
    void changeMyPassword_returns400_whenCurrentPasswordMismatch() throws Exception {
        expectServiceError(ErrorCode.CURRENT_PASSWORD_MISMATCH);

        mockMvc.perform(passwordChangeRequest("wrongpass", "newpass123", "newpass123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password returns 400 when the confirmation does not match")
    void changeMyPassword_returns400_whenPasswordMismatch() throws Exception {
        expectServiceError(ErrorCode.PASSWORD_MISMATCH);

        mockMvc.perform(passwordChangeRequest("oldpass123", "newpass123", "otherpass1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password returns 409 for Kakao-only accounts")
    void changeMyPassword_returns409_whenKakaoOnly() throws Exception {
        expectServiceError(ErrorCode.PASSWORD_CHANGE_NOT_AVAILABLE);

        mockMvc.perform(passwordChangeRequest("oldpass123", "newpass123", "newpass123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password returns 403 for suspended accounts")
    void changeMyPassword_returns403_whenSuspended() throws Exception {
        expectServiceError(ErrorCode.SUSPENDED_USER);

        mockMvc.perform(passwordChangeRequest("oldpass123", "newpass123", "newpass123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUSPENDED_USER"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password returns 403 for withdrawal pending accounts")
    void changeMyPassword_returns403_whenWithdrawalPending() throws Exception {
        expectServiceError(ErrorCode.WITHDRAWAL_PENDING_USER);

        mockMvc.perform(passwordChangeRequest("oldpass123", "newpass123", "newpass123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WITHDRAWAL_PENDING_USER"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password returns 403 for withdrawn accounts")
    void changeMyPassword_returns403_whenWithdrawn() throws Exception {
        expectServiceError(ErrorCode.WITHDRAWN_USER);

        mockMvc.perform(passwordChangeRequest("oldpass123", "newpass123", "newpass123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WITHDRAWN_USER"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password returns 400 when currentPassword is missing")
    void changeMyPassword_returns400_whenCurrentPasswordMissing() throws Exception {
        mockMvc.perform(
                        passwordChangeRequest(
                                """
                                {
                                  "password": "newpass1",
                                  "passwordConfirm": "newpass1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // 현재 비밀번호가 없으면 서비스까지 내려가 500이 되지 않아야 한다.
        verifyNoInteractions(passwordChangeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"   \""})
    @DisplayName(
            "PATCH /api/v1/users/me/password returns 400 when currentPassword is null or blank")
    void changeMyPassword_returns400_whenCurrentPasswordBlank(String currentPasswordJson)
            throws Exception {
        mockMvc.perform(
                        passwordChangeRequest(
                                """
                                {
                                  "currentPassword": %s,
                                  "password": "newpass1",
                                  "passwordConfirm": "newpass1"
                                }
                                """
                                        .formatted(currentPasswordJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(passwordChangeService);
    }

    private void expectServiceError(ErrorCode errorCode) {
        doThrow(new BusinessException(errorCode))
                .when(passwordChangeService)
                .changePassword(any(), any());
    }

    private MockHttpServletRequestBuilder passwordChangeRequest(
            String currentPassword, String password, String passwordConfirm) {
        return passwordChangeRequest(
                """
                {
                  "currentPassword": "%s",
                  "password": "%s",
                  "passwordConfirm": "%s"
                }
                """
                        .formatted(currentPassword, password, passwordConfirm));
    }

    private MockHttpServletRequestBuilder passwordChangeRequest(String body) {
        return patch("/api/v1/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private UserProfileResponse sampleProfile() {
        return sampleProfile(AccountLoginType.EMAIL);
    }

    private UserProfileResponse sampleProfile(AccountLoginType loginType) {
        return new UserProfileResponse(
                1L,
                "홍길동",
                "길동",
                "소개글",
                LocalDate.of(2000, 1, 1),
                Gender.MALE,
                new RegionResponse(123L, "강남구", 2, "11680", null, null),
                List.of(PostingCategory.WELFARE),
                loginType);
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from("gather_refresh_token", "")
                .path("/api/v1/auth")
                .httpOnly(true)
                .sameSite("Lax")
                .maxAge(0)
                .build();
    }
}
