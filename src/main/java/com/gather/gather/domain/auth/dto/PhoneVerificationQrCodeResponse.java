package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "휴대폰 인증용 SMS QR 코드")
public record PhoneVerificationQrCodeResponse(
        @Schema(description = "프론트에서 이미지 src로 사용할 PNG data URL") String qrCode) {}
