package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 인증 코드 확인 요청")
public record EmailVerificationConfirmRequest(
        @Schema(description = "인증을 진행한 이메일", example = "test@example.com")
                @NotBlank
                @Email
                @Size(max = 255)
                String email,
        @Schema(description = "사용자가 입력한 인증 코드", example = "123456")
                @NotBlank
                String code) {}
