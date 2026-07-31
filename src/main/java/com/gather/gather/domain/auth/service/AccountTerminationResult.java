package com.gather.gather.domain.auth.service;

import java.time.LocalDateTime;
import java.util.Objects;

/** 처리 결과 tag와 최초 완료 또는 접수 시각을 함께 전달하는 payload. */
public record AccountTerminationResult(
        AccountTerminationOutcome outcome, LocalDateTime occurredAt) {

    public AccountTerminationResult {
        Objects.requireNonNull(outcome, "탈퇴 처리 결과는 필수입니다.");
        Objects.requireNonNull(occurredAt, "탈퇴 처리 발생 시각은 필수입니다.");
    }
}
