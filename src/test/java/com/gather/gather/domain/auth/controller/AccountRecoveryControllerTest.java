package com.gather.gather.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.dto.AccountRecoveryRequest;
import com.gather.gather.domain.auth.dto.AccountRecoveryResponse;
import com.gather.gather.domain.auth.service.AccountRecoveryService;
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
}
