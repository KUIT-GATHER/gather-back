package com.gather.gather.domain.auth.dto;

import com.gather.gather.domain.auth.entity.PhoneVerificationPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "휴대폰 문자 인증 시작 요청")
public record PhoneVerificationStartRequest(
        @Schema(description = "인증할 휴대폰 번호. 하이픈과 공백은 서버에서 제거합니다.", example = "01012345678")
                @NotBlank
                @Size(max = 20)
                String phoneNumber,
        @Schema(
                        description = "휴대폰 인증 목적",
                        example = "SIGNUP",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                PhoneVerificationPurpose purpose) {}
