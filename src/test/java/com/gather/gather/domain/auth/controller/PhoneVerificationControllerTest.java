package com.gather.gather.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.auth.dto.PhoneVerificationConfirmResponse;
import com.gather.gather.domain.auth.dto.PhoneVerificationQrCodeResponse;
import com.gather.gather.domain.auth.dto.PhoneVerificationStartRequest;
import com.gather.gather.domain.auth.dto.PhoneVerificationStartResponse;
import com.gather.gather.domain.auth.dto.PhoneVerificationStatus;
import com.gather.gather.domain.auth.service.PhoneVerificationService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PhoneVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class PhoneVerificationControllerTest {

    private static final String VERIFICATION_ID = "5c5d5db1-4187-43d0-8580-672307994878";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PhoneVerificationService phoneVerificationService;

    @Test
    @DisplayName("인증 시작은 세션을 생성하고 201 공통 응답으로 반환한다")
    void start_returnsCreated() throws Exception {
        when(phoneVerificationService.start(any(PhoneVerificationStartRequest.class)))
                .thenReturn(
                        new PhoneVerificationStartResponse(
                                VERIFICATION_ID,
                                "16663538",
                                "GATHER-7F2K9Q8M4P",
                                Instant.parse("2026-08-09T06:50:00Z")));

        mockMvc.perform(
                        post("/api/v1/auth/phone-verifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phoneNumber\":\"010-1234-5678\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verificationId").value(VERIFICATION_ID))
                .andExpect(jsonPath("$.data.receiverNumber").value("16663538"))
                .andExpect(jsonPath("$.data.messageText").value("GATHER-7F2K9Q8M4P"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-08-09T06:50:00Z"));
    }

    @Test
    @DisplayName("빈 전화번호는 서비스 호출 전에 400으로 거부한다")
    void start_rejectsBlankPhoneNumber() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/phone-verifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phoneNumber\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(phoneVerificationService);
    }

    @Test
    @DisplayName("QR API는 request body 없이 경로의 verificationId만 서비스에 전달한다")
    void createQrCode_usesOnlyPathVerificationId() throws Exception {
        when(phoneVerificationService.createQrCode(VERIFICATION_ID))
                .thenReturn(new PhoneVerificationQrCodeResponse("data:image/png;base64,cXI="));

        mockMvc.perform(
                        post(
                                "/api/v1/auth/phone-verifications/{verificationId}/qr-code",
                                VERIFICATION_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.qrCode").value("data:image/png;base64,cXI="));

        verify(phoneVerificationService).createQrCode(eq(VERIFICATION_ID));
    }

    @Test
    @DisplayName("confirm API는 request body 없이 PENDING 상태를 반환한다")
    void confirm_returnsPending() throws Exception {
        when(phoneVerificationService.confirm(VERIFICATION_ID))
                .thenReturn(new PhoneVerificationConfirmResponse(PhoneVerificationStatus.PENDING));

        mockMvc.perform(
                        post(
                                "/api/v1/auth/phone-verifications/{verificationId}/confirm",
                                VERIFICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(phoneVerificationService).confirm(VERIFICATION_ID);
    }

    @Test
    @DisplayName("올바르지 않은 verificationId 형식은 400으로 변환한다")
    void confirm_rejectsMalformedVerificationId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/phone-verifications/not-a-uuid/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(phoneVerificationService);
    }
}
