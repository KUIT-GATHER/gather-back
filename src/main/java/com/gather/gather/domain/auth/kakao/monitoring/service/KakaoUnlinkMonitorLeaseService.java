package com.gather.gather.domain.auth.kakao.monitoring.service;

import com.gather.gather.domain.auth.entity.KakaoUnlinkMonitorControl;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkMonitorLease;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkMonitorLeaseAcquireResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkMonitorLeaseFinishResult;
import com.gather.gather.domain.auth.kakao.monitoring.model.KakaoUnlinkOperationalFailureType;
import com.gather.gather.domain.auth.repository.KakaoUnlinkMonitorControlRepository;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoUnlinkMonitorLeaseService {

    private final KakaoUnlinkMonitorControlRepository controlRepository;
    private final KakaoUnlinkMonitorTokenGenerator tokenGenerator;

    @Transactional
    public KakaoUnlinkMonitorLeaseAcquireResult tryAcquire(String owner, Duration duration) {
        requireOwner(owner);
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("monitor lease duration은 양수여야 합니다.");
        }

        KakaoUnlinkMonitorControl control = findControlForUpdate();
        LocalDateTime databaseNow = controlRepository.currentUtcDateTime();
        if (control.hasValidLease(databaseNow)) {
            return KakaoUnlinkMonitorLeaseAcquireResult.busy();
        }

        LocalDateTime expiresAt;
        try {
            expiresAt = databaseNow.plus(duration);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException("monitor lease duration이 너무 큽니다.", exception);
        }
        String token = tokenGenerator.generate();
        long sequence = control.acquire(token, owner, databaseNow, expiresAt);
        return KakaoUnlinkMonitorLeaseAcquireResult.acquired(
                new KakaoUnlinkMonitorLease(sequence, owner, token, databaseNow, expiresAt));
    }

    @Transactional
    public KakaoUnlinkMonitorLeaseFinishResult complete(KakaoUnlinkMonitorLease lease) {
        requireLease(lease);
        KakaoUnlinkMonitorControl control = findControlForUpdate();
        LocalDateTime databaseNow = controlRepository.currentUtcDateTime();
        return control.complete(lease.scanSequence(), lease.owner(), lease.token(), databaseNow)
                ? KakaoUnlinkMonitorLeaseFinishResult.COMPLETED
                : KakaoUnlinkMonitorLeaseFinishResult.LEASE_LOST;
    }

    @Transactional
    public KakaoUnlinkMonitorLeaseFinishResult fail(
            KakaoUnlinkMonitorLease lease, KakaoUnlinkOperationalFailureType failureType) {
        requireLease(lease);
        if (failureType == null) {
            throw new IllegalArgumentException("monitor failure type은 필수입니다.");
        }
        KakaoUnlinkMonitorControl control = findControlForUpdate();
        LocalDateTime databaseNow = controlRepository.currentUtcDateTime();
        return control.fail(
                        lease.scanSequence(),
                        lease.owner(),
                        lease.token(),
                        failureType.name(),
                        databaseNow)
                ? KakaoUnlinkMonitorLeaseFinishResult.FAILED
                : KakaoUnlinkMonitorLeaseFinishResult.LEASE_LOST;
    }

    private KakaoUnlinkMonitorControl findControlForUpdate() {
        return controlRepository
                .findSingletonForUpdate()
                .orElseThrow(
                        () -> new IllegalStateException("Kakao unlink monitor control이 없습니다."));
    }

    private static void requireOwner(String owner) {
        if (owner == null || owner.isBlank() || owner.length() > 128) {
            throw new IllegalArgumentException("monitor owner 값이 올바르지 않습니다.");
        }
    }

    private static void requireLease(KakaoUnlinkMonitorLease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("monitor lease는 필수입니다.");
        }
    }
}
