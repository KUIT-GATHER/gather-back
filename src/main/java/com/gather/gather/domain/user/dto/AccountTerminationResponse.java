package com.gather.gather.domain.user.dto;

import com.gather.gather.domain.auth.service.AccountTerminationOutcome;
import com.gather.gather.domain.auth.service.AccountTerminationResult;
import java.time.Instant;
import java.time.ZoneOffset;

public record AccountTerminationResponse(Status status, Instant occurredAt) {

    public static AccountTerminationResponse from(AccountTerminationResult result) {
        return new AccountTerminationResponse(
                Status.from(result.outcome()), result.occurredAt().toInstant(ZoneOffset.UTC));
    }

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
