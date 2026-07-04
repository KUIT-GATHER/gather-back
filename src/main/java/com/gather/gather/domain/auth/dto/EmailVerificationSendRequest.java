package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 인증 코드 발송 요청")
public record EmailVerificationSendRequest(
        @Schema(description = "인증 코드를 받을 이메일", example = "test@example.com")
                @NotBlank
                @Email
                @Size(max = 255)
                String email) {}
