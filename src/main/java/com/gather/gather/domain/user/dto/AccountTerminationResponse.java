package com.gather.gather.domain.user.dto;

import com.gather.gather.domain.auth.service.AccountTerminationOutcome;
import com.gather.gather.domain.auth.service.AccountTerminationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.ZoneOffset;

@Schema(description = "회원 탈퇴 처리 결과")
public record AccountTerminationResponse(
        @Schema(description = "탈퇴 처리 상태", example = "COMPLETED") Status status,
        @Schema(description = "탈퇴 완료 또는 연결 해제 작업 접수 시각", example = "2026-08-01T14:00:00Z")
                Instant occurredAt) {

    public static AccountTerminationResponse from(AccountTerminationResult result) {
        return new AccountTerminationResponse(
                Status.from(result.outcome()), result.occurredAt().toInstant(ZoneOffset.UTC));
    }

    @Schema(description = "회원 탈퇴 처리 상태")
    public enum Status {
        COMPLETED,
        ACCEPTED;

        private static Status from(AccountTerminationOutcome outcome) {
            return switch (outcome) {
                case COMPLETED -> COMPLETED;
                case ACCEPTED -> ACCEPTED;
            };
        }
    }
}
