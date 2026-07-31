package com.gather.gather.domain.auth.service;

import java.time.LocalDateTime;

public record AccountTerminationResult(
        AccountTerminationOutcome outcome, LocalDateTime occurredAt) {}
