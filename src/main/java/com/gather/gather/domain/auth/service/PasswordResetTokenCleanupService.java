package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 만료된 재설정 토큰을 파기한다. credential이므로 보존 기간을 두지 않고 만료 즉시 삭제 대상으로 본다. */
@Service
@RequiredArgsConstructor
public class PasswordResetTokenCleanupService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    @Transactional
    public int cleanupExpiredTokens() {
        return passwordResetTokenRepository.deleteAllExpiredAtOrBefore(LocalDateTime.now(clock));
    }
}
