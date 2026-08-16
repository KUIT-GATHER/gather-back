package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.repository.PhoneVerificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PhoneVerificationCleanupService {

    static final int RETENTION_HOURS = 22;

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final Clock clock;

    @Transactional
    public int cleanupOverdueVerifications() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(RETENTION_HOURS);
        return phoneVerificationRepository.deleteAllCreatedAtOrBefore(cutoff);
    }
}
