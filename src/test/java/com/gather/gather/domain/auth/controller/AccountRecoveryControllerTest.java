package com.gather.gather.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.dto.AccountRecoveryRequest;
import com.gather.gather.domain.auth.dto.AccountRecoveryResponse;
import com.gather.gather.domain.auth.dto.PasswordResetAuthorityRequest;
import com.gather.gather.domain.auth.dto.PasswordResetRequest;
import com.gather.gather.domain.auth.dto.PasswordResetTokenResponse;
import com.gather.gather.domain.auth.service.AccountRecoveryService;
import com.gather.gather.domain.auth.service.PasswordResetService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountRecoveryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountRecoveryControllerTest {

    private static final UUID VERIFICATION_ID =
            UUID.fromString("5c5d5db1-4187-43d0-8580-672307994878");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AccountRecoveryService accountRecoveryService;
    @MockitoBean private PasswordResetService passwordResetService;

    @Test
    @DisplayName("이메일 로그인 계정의 아이디 찾기 결과를 반환한다")
    void recoverEmail_returnsEmailAccount() throws Exception {
        when(accountRecoveryService.recoverEmail(any(AccountRecoveryRequest.class)))
                .thenReturn(AccountRecoveryResponse.email("user@example.com"));

        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "phoneVerificationId": "%s"
                                        }
                                        """
                                                .formatted(VERIFICATION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginType").value("EMAIL"))
                .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    @Test
    @DisplayName("카카오 전용 계정은 이메일 없이 KAKAO 결과를 반환한다")
    void recoverEmail_returnsKakaoAccount() throws Exception {
        when(accountRecoveryService.recoverEmail(any(AccountRecoveryRequest.class)))
                .thenReturn(AccountRecoveryResponse.kakao());

        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "phoneVerificationId": "%s"
                                        }
                                        """
                                                .formatted(VERIFICATION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginType").value("KAKAO"))
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    @DisplayName("아이디 찾기 요청은 클라이언트 전화번호를 서비스 계약에 전달하지 않는다")
    void recoverEmail_usesOnlyPhoneVerificationId() throws Exception {
        when(accountRecoveryService.recoverEmail(any(AccountRecoveryRequest.class)))
                .thenReturn(AccountRecoveryResponse.email("user@example.com"));

        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "phoneVerificationId": "%s",
                                          "phoneNumber": "01099999999"
                                        }
                                        """
                                                .formatted(VERIFICATION_ID)))
                .andExpect(status().isOk());

        ArgumentCaptor<AccountRecoveryRequest> captor =
                ArgumentCaptor.forClass(AccountRecoveryRequest.class);
        verify(accountRecoveryService).recoverEmail(captor.capture());
        assertThat(captor.getValue().phoneVerificationId()).isEqualTo(VERIFICATION_ID);
        assertThat(AccountRecoveryRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("phoneVerificationId");
    }

    @Test
    @DisplayName("휴대폰 인증 ID가 누락되면 서비스 호출 전에 거부한다")
    void recoverEmail_rejectsMissingVerificationId() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(accountRecoveryService);
    }

    @Test
    @DisplayName("휴대폰 인증 ID가 UUID 형식이 아니면 서비스 호출 전에 거부한다")
    void recoverEmail_rejectsInvalidVerificationId() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "phoneVerificationId": "not-a-uuid"
                                        }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(accountRecoveryService);
    }

    @Test
    @DisplayName("비밀번호 재설정 권한 발급은 재설정 토큰을 반환한다")
    void issuePasswordResetToken_returnsToken() throws Exception {
        when(passwordResetService.issueToken(any(PasswordResetAuthorityRequest.class)))
                .thenReturn(new PasswordResetTokenResponse("A".repeat(43)));

        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "phoneVerificationId": "%s"
                                        }
                                        """
                                                .formatted(VERIFICATION_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passwordResetToken").value("A".repeat(43)))
                .andExpect(jsonPath("$.error").doesNotExist());

        ArgumentCaptor<PasswordResetAuthorityRequest> captor =
                ArgumentCaptor.forClass(PasswordResetAuthorityRequest.class);
        verify(passwordResetService).issueToken(captor.capture());
        assertThat(captor.getValue().phoneVerificationId()).isEqualTo(VERIFICATION_ID);
        assertThat(PasswordResetAuthorityRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("phoneVerificationId");
    }

    @Test
    @DisplayName("카카오 전용 계정의 재설정 권한 요청은 409로 응답한다")
    void issuePasswordResetToken_kakaoOnlyAccount_returnsConflict() throws Exception {
        when(passwordResetService.issueToken(any(PasswordResetAuthorityRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.PASSWORD_RESET_NOT_AVAILABLE));

        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "phoneVerificationId": "%s"
                                        }
                                        """
                                                .formatted(VERIFICATION_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_RESET_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("재설정 권한 요청도 휴대폰 인증 ID가 없으면 서비스 호출 전에 거부한다")
    void issuePasswordResetToken_rejectsMissingVerificationId() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    @DisplayName("비밀번호 재설정은 본문 없는 성공 응답을 반환하고 요청 값을 그대로 전달한다")
    void resetPassword_returnsSuccessWithoutData() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "passwordResetToken": "%s",
                                          "password": "newpass123",
                                          "passwordConfirm": "newpass123"
                                        }
                                        """
                                                .formatted("A".repeat(43))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());

        ArgumentCaptor<PasswordResetRequest> captor =
                ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(passwordResetService).resetPassword(captor.capture());
        PasswordResetRequest captured = captor.getValue();
        assertThat(captured.passwordResetToken()).isEqualTo("A".repeat(43));
        assertThat(captured.password()).isEqualTo("newpass123");
        assertThat(captured.passwordConfirm()).isEqualTo("newpass123");
        assertThat(captured.toString()).doesNotContain("A".repeat(43)).doesNotContain("newpass123");
    }

    @Test
    @DisplayName("재설정 토큰이 유효하지 않거나 만료되면 401로 응답한다")
    void resetPassword_invalidOrExpiredToken_returnsUnauthorized() throws Exception {
        assertResetPasswordError(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        assertResetPasswordError(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
    }

    private void assertResetPasswordError(ErrorCode errorCode) throws Exception {
        doThrow(new BusinessException(errorCode))
                .when(passwordResetService)
                .resetPassword(any(PasswordResetRequest.class));

        mockMvc.perform(
                        post("/api/v1/auth/account-recoveries/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "passwordResetToken": "%s",
                                          "password": "newpass123",
                                          "passwordConfirm": "newpass123"
                                        }
                                        """
                                                .formatted("A".repeat(43))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(errorCode.name()));
    }
}
