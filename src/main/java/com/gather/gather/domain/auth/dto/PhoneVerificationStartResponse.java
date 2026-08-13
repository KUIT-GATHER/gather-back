package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "휴대폰 문자 인증 세션")
public record PhoneVerificationStartResponse(
        @Schema(description = "인증 세션 식별자", format = "uuid") String verificationId,
        @Schema(description = "문자를 보낼 OCTOMO 대표번호", example = "16663538") String receiverNumber,
        @Schema(
                        description = "사용자가 줄바꿈을 포함해 그대로 전송할 전체 SMS 본문",
                        example =
                                "[Gather]\n전화번호 인증을 위한 문자입니다.\n본 문자를 전송하시면 전화번호 인증이 자동으로 완료됩니다.\n\n인증코드: [GATHER-7F2K9Q8M4P]")
                String messageText,
        @Schema(description = "문자 전송 및 확인 만료 시각(UTC)") Instant expiresAt) {}
