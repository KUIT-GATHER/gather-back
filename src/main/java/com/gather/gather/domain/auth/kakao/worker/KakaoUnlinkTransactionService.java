package com.gather.gather.domain.auth.kakao.worker;

import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskErrorType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControl;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.KakaoUnlinkTaskRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkWorkerControlRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.SocialAccountCryptoException;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class KakaoUnlinkTransactionService {

    private final KakaoUnlinkWorkerControlRepository controlRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final KakaoUnlinkTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SocialAccountProviderIdCipher providerIdCipher;
    private final KakaoUnlinkWorkerProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public KakaoUnlinkPreflightOutcome preflight(KakaoUnlinkClaim claim) {
        KakaoUnlinkTask task = taskRepository.findById(claim.taskId()).orElse(null);
        if (task == null) {
            return KakaoUnlinkPreflightOutcome.CLAIM_LOST;
        }
        LocalDateTime databaseNow = taskRepository.currentUtcDateTime();
        if (!matchesClaim(task, claim, databaseNow)) {
            return KakaoUnlinkPreflightOutcome.CLAIM_LOST;
        }
        SocialAccount account = task.getSocialAccount();
        User user = account.getUser();
        if (!matchesInvariant(account, user, claim)) {
            return KakaoUnlinkPreflightOutcome.STALE;
        }
        if (user.getStatus() != UserStatus.WITHDRAWAL_PENDING) {
            return KakaoUnlinkPreflightOutcome.STALE;
        }
        if (account.isUnlinkPending()) {
            return KakaoUnlinkPreflightOutcome.RESERVE;
        }
        return account.isUnlinked()
                ? KakaoUnlinkPreflightOutcome.LOCAL_FINALIZE
                : KakaoUnlinkPreflightOutcome.STALE;
    }

    @Transactional
    public KakaoUnlinkReservation reserveAttempt(KakaoUnlinkClaim claim) {
        KakaoUnlinkWorkerControl control =
                controlRepository
                        .findSingletonForUpdate()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Kakao unlink worker control이 없습니다."));
        if (!control.isActive()) {
            return KakaoUnlinkReservation.of(KakaoUnlinkReservation.Outcome.BLOCKED);
        }

        SocialAccount account =
                socialAccountRepository.findByIdForUpdate(claim.socialAccountId()).orElse(null);
        KakaoUnlinkTask task = taskRepository.findByIdForUpdate(claim.taskId()).orElse(null);
        User user = userRepository.findByIdForUpdate(claim.userId()).orElse(null);
        LocalDateTime leaseNow = taskRepository.currentUtcDateTime();
        if (task == null || !matchesClaim(task, claim, leaseNow)) {
            return KakaoUnlinkReservation.of(KakaoUnlinkReservation.Outcome.CLAIM_LOST);
        }
        LocalDateTime attemptNow = LocalDateTime.now(clock);
        if (account == null
                || user == null
                || !matchesInvariant(account, user, claim)
                || !account.isUnlinkPending()
                || user.getStatus() != UserStatus.WITHDRAWAL_PENDING) {
            task.stale(claim.claimToken(), leaseNow, attemptNow);
            return KakaoUnlinkReservation.of(KakaoUnlinkReservation.Outcome.TERMINAL);
        }
        if (task.getAttemptCount() >= properties.maximumAttempts()) {
            task.dead(
                    claim.claimToken(),
                    leaseNow,
                    attemptNow,
                    null,
                    null,
                    KakaoUnlinkTaskErrorType.ATTEMPT_EXHAUSTED);
            return KakaoUnlinkReservation.of(KakaoUnlinkReservation.Outcome.TERMINAL);
        }

        String decryptedProviderId;
        try {
            decryptedProviderId = providerIdCipher.decrypt(account.encryptedProviderUserId());
        } catch (SocialAccountCryptoException exception) {
            return terminalReservationFailure(
                    task, claim, leaseNow, attemptNow, KakaoUnlinkTaskErrorType.SECURITY);
        } catch (IllegalStateException exception) {
            return terminalReservationFailure(
                    task, claim, leaseNow, attemptNow, KakaoUnlinkTaskErrorType.INVARIANT);
        }

        long kakaoUserId;
        try {
            kakaoUserId = Long.parseLong(decryptedProviderId);
            if (kakaoUserId <= 0) {
                throw new NumberFormatException("non-positive provider id");
            }
        } catch (NumberFormatException exception) {
            return terminalReservationFailure(
                    task, claim, leaseNow, attemptNow, KakaoUnlinkTaskErrorType.INVARIANT);
        }
        int attemptCount =
                task.reserveAttempt(
                        claim.claimToken(), leaseNow, attemptNow, properties.maximumAttempts());
        return KakaoUnlinkReservation.reserved(
                new KakaoUnlinkAttempt(claim, kakaoUserId, attemptCount));
    }

    private KakaoUnlinkReservation terminalReservationFailure(
            KakaoUnlinkTask task,
            KakaoUnlinkClaim claim,
            LocalDateTime leaseNow,
            LocalDateTime attemptNow,
            KakaoUnlinkTaskErrorType errorType) {
        task.dead(claim.claimToken(), leaseNow, attemptNow, null, null, errorType);
        log.error(
                "Kakao unlink reservation became DEAD: taskId={}, retryCycle={}, attemptCount={}, errorType={}",
                claim.taskId(),
                claim.retryCycle(),
                task.getAttemptCount(),
                errorType);
        return KakaoUnlinkReservation.of(KakaoUnlinkReservation.Outcome.TERMINAL);
    }

    @Transactional
    public void markStale(KakaoUnlinkClaim claim) {
        SocialAccount account =
                socialAccountRepository.findByIdForUpdate(claim.socialAccountId()).orElse(null);
        KakaoUnlinkTask task = taskRepository.findByIdForUpdate(claim.taskId()).orElse(null);
        User user = userRepository.findByIdForUpdate(claim.userId()).orElse(null);
        LocalDateTime leaseNow = taskRepository.currentUtcDateTime();
        if (account != null
                && task != null
                && user != null
                && matchesClaim(task, claim, leaseNow)) {
            task.stale(claim.claimToken(), leaseNow, LocalDateTime.now(clock));
        }
    }

    private boolean matchesClaim(
            KakaoUnlinkTask task, KakaoUnlinkClaim claim, LocalDateTime leaseNow) {
        return task.getId().equals(claim.taskId())
                && task.getGeneration() == claim.generation()
                && task.getRetryCycle() == claim.retryCycle()
                && task.hasOwnedValidClaim(claim.claimToken(), leaseNow);
    }

    private boolean matchesInvariant(SocialAccount account, User user, KakaoUnlinkClaim claim) {
        return account.getId().equals(claim.socialAccountId())
                && account.getUser().getId().equals(claim.userId())
                && user.getId().equals(claim.userId())
                && account.getProvider() == SocialProvider.KAKAO
                && account.matchesGeneration(claim.generation());
    }
}
