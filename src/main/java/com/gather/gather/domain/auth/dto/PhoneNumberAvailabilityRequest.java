package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "전화번호 중복 확인 요청")
public record PhoneNumberAvailabilityRequest(
        @Schema(description = "중복 확인할 전화번호", example = "01012345678") @NotBlank @Size(max = 20)
                String phoneNumber) {}
