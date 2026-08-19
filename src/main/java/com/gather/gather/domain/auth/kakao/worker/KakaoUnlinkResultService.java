package com.gather.gather.domain.auth.kakao.worker;

import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTaskErrorType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkWorkerControl;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkDisposition;
import com.gather.gather.domain.auth.kakao.admin.client.KakaoAdminUnlinkResult;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkTaskRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkWorkerControlRepository;
import com.gather.gather.domain.auth.repository.PasswordResetTokenRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.user.service.ProfileImageDeletionService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoUnlinkResultService {

    private final KakaoUnlinkWorkerControlRepository controlRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final KakaoUnlinkTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final ProfileImageDeletionService profileImageDeletionService;
    private final KakaoUnlinkRetryPolicy retryPolicy;
    private final KakaoUnlinkWorkerProperties properties;
    private final Clock clock;

    @Transactional
    public KakaoUnlinkProcessingResult apply(
            KakaoUnlinkAttempt attempt, KakaoAdminUnlinkResult result) {
        if (result.disposition() == KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION) {
            throw new IllegalArgumentException("설정 오류는 applyConfigurationFailure로 처리해야 합니다.");
        }
        LockedContextResult locked = lockAndValidate(attempt.claim(), attempt.attemptCount());
        if (locked.context() == null) {
            return locked.outcome();
        }
        LockedContext context = locked.context();
        LocalDateTime resultNow = LocalDateTime.now(clock);
        KakaoUnlinkTask task = context.task();

        return switch (result.disposition()) {
            case SUCCESS, ALREADY_UNLINKED -> {
                finalizeWithdrawal(context, resultNow);
                yield KakaoUnlinkProcessingResult.SUCCEEDED;
            }
            case RETRYABLE -> {
                if (task.getAttemptCount() >= properties.maximumAttempts()) {
                    task.dead(
                            attempt.claim().claimToken(),
                            context.leaseNow(),
                            resultNow,
                            result.httpStatus(),
                            result.kakaoCode(),
                            KakaoUnlinkTaskErrorType.ATTEMPT_EXHAUSTED);
                    log.error(
                            "Kakao unlink task exhausted attempts: taskId={}, retryCycle={}, attemptCount={}, httpStatus={}, kakaoCode={}",
                            attempt.claim().taskId(),
                            attempt.claim().retryCycle(),
                            task.getAttemptCount(),
                            result.httpStatus(),
                            result.kakaoCode());
                    yield KakaoUnlinkProcessingResult.DEAD;
                } else {
                    LocalDateTime nextAttemptAt =
                            retryPolicy.nextAttemptAt(
                                    resultNow, task.getAttemptCount(), result.retryAfterAt());
                    task.scheduleRetry(
                            attempt.claim().claimToken(),
                            context.leaseNow(),
                            nextAttemptAt,
                            resultNow,
                            result.httpStatus(),
                            result.kakaoCode());
                    yield KakaoUnlinkProcessingResult.RETRY_SCHEDULED;
                }
            }
            case PERMANENT_REQUEST -> {
                markDead(context, attempt, result, resultNow, KakaoUnlinkTaskErrorType.REQUEST);
                yield KakaoUnlinkProcessingResult.DEAD;
            }
            case RESPONSE_FAILURE -> {
                markDead(context, attempt, result, resultNow, KakaoUnlinkTaskErrorType.RESPONSE);
                yield KakaoUnlinkProcessingResult.DEAD;
            }
            case SECURITY_FAILURE -> {
                markDead(context, attempt, result, resultNow, KakaoUnlinkTaskErrorType.SECURITY);
                log.error(
                        "Kakao unlink response security failure: taskId={}, httpStatus={}, kakaoCode={}",
                        attempt.claim().taskId(),
                        result.httpStatus(),
                        result.kakaoCode());
                yield KakaoUnlinkProcessingResult.DEAD;
            }
            case UNKNOWN_PERMANENT -> {
                markDead(context, attempt, result, resultNow, KakaoUnlinkTaskErrorType.UNKNOWN);
                log.error(
                        "Unknown permanent Kakao unlink failure: taskId={}, httpStatus={}, kakaoCode={}",
                        attempt.claim().taskId(),
                        result.httpStatus(),
                        result.kakaoCode());
                yield KakaoUnlinkProcessingResult.DEAD;
            }
            case PERMANENT_CONFIGURATION -> throw new IllegalStateException("unreachable");
        };
    }

    @Transactional
    public KakaoUnlinkProcessingResult applyConfigurationFailure(
            KakaoUnlinkAttempt attempt, KakaoAdminUnlinkResult result) {
        if (result.disposition() != KakaoAdminUnlinkDisposition.PERMANENT_CONFIGURATION) {
            throw new IllegalArgumentException("설정 오류 결과만 처리할 수 있습니다.");
        }
        KakaoUnlinkWorkerControl control =
                controlRepository
                        .findSingletonForUpdate()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Kakao unlink worker control이 없습니다."));
        LockedContextResult locked = lockAndValidate(attempt.claim(), attempt.attemptCount());
        if (locked.context() == null) {
            return locked.outcome();
        }
        LockedContext context = locked.context();
        LocalDateTime resultNow = LocalDateTime.now(clock);
        context.task()
                .dead(
                        attempt.claim().claimToken(),
                        context.leaseNow(),
                        resultNow,
                        result.httpStatus(),
                        result.kakaoCode(),
                        KakaoUnlinkTaskErrorType.CONFIGURATION);
        control.blockConfiguration(resultNow, result.httpStatus(), result.kakaoCode());
        log.error(
                "Kakao unlink worker configuration blocked: taskId={}, httpStatus={}, kakaoCode={}",
                attempt.claim().taskId(),
                result.httpStatus(),
                result.kakaoCode());
        return KakaoUnlinkProcessingResult.CONFIGURATION_BLOCKED;
    }

    @Transactional
    public KakaoUnlinkProcessingResult finalizeLocally(KakaoUnlinkClaim claim) {
        LockedContextResult locked = lockAndValidate(claim, null);
        if (locked.context() == null) {
            return locked.outcome();
        }
        LockedContext context = locked.context();
        if (!context.account().isUnlinked()
                || context.user().getStatus() != UserStatus.WITHDRAWAL_PENDING) {
            context.task().stale(claim.claimToken(), context.leaseNow(), LocalDateTime.now(clock));
            return KakaoUnlinkProcessingResult.STALE;
        }
        finalizeWithdrawal(context, LocalDateTime.now(clock));
        return KakaoUnlinkProcessingResult.SUCCEEDED;
    }

    private LockedContextResult lockAndValidate(
            KakaoUnlinkClaim claim, Integer expectedAttemptCount) {
        // Global lock order after an optional WorkerControl lock: SocialAccount -> Task -> User.
        SocialAccount account =
                socialAccountRepository.findByIdForUpdate(claim.socialAccountId()).orElse(null);
        KakaoUnlinkTask task = taskRepository.findByIdForUpdate(claim.taskId()).orElse(null);
        User user = userRepository.findByIdForUpdate(claim.userId()).orElse(null);
        LocalDateTime leaseNow = taskRepository.currentUtcDateTime();
        if (account == null
                || task == null
                || user == null
                || !task.hasOwnedValidClaim(claim.claimToken(), leaseNow)
                || task.getRetryCycle() != claim.retryCycle()) {
            return LockedContextResult.outcome(KakaoUnlinkProcessingResult.CLAIM_LOST);
        }
        if (expectedAttemptCount != null && task.getAttemptCount() != expectedAttemptCount) {
            return LockedContextResult.outcome(KakaoUnlinkProcessingResult.CLAIM_LOST);
        }
        if (!account.getId().equals(claim.socialAccountId())
                || !account.getUser().getId().equals(claim.userId())
                || account.getProvider() != SocialProvider.KAKAO
                || !account.matchesGeneration(claim.generation())
                || (!account.isUnlinkPending() && !account.isUnlinked())
                || task.getGeneration() != claim.generation()
                || !task.getSocialAccount().getId().equals(account.getId())
                || user.getStatus() != UserStatus.WITHDRAWAL_PENDING) {
            task.stale(claim.claimToken(), leaseNow, LocalDateTime.now(clock));
            return LockedContextResult.outcome(KakaoUnlinkProcessingResult.STALE);
        }
        return LockedContextResult.context(
                new LockedContext(account, task, user, leaseNow, claim.claimToken()));
    }

    private void finalizeWithdrawal(LockedContext context, LocalDateTime resultNow) {
        String email = context.user().getEmail();
        String profileImageKey = context.user().getProfileImageKey();
        context.account().finalizeUnlinkAndPurgeDirectIdentifiers(resultNow);
        refreshTokenRepository.deleteAllByUserId(context.user().getId());
        // 탈퇴 접수 시 이미 파기하지만, 비동기 마무리가 유일한 종료 지점인 재시도 경로를 위해 한 번 더 확인한다.
        passwordResetTokenRepository.deleteAllByUserId(context.user().getId());
        if (email != null) {
            emailVerificationRepository.deleteAllByEmail(email);
        }
        profileImageDeletionService.scheduleDeletion(
                context.user().getId(), profileImageKey, resultNow);
        context.user().completePendingWithdrawal(resultNow);
        context.user().anonymize(resultNow);
        context.task().succeed(context.claimToken(), context.leaseNow(), resultNow);
    }

    private void markDead(
            LockedContext context,
            KakaoUnlinkAttempt attempt,
            KakaoAdminUnlinkResult result,
            LocalDateTime resultNow,
            KakaoUnlinkTaskErrorType errorType) {
        context.task()
                .dead(
                        attempt.claim().claimToken(),
                        context.leaseNow(),
                        resultNow,
                        result.httpStatus(),
                        result.kakaoCode(),
                        errorType);
        log.error(
                "Kakao unlink task became DEAD: taskId={}, retryCycle={}, attemptCount={}, errorType={}, httpStatus={}, kakaoCode={}",
                attempt.claim().taskId(),
                attempt.claim().retryCycle(),
                attempt.attemptCount(),
                errorType,
                result.httpStatus(),
                result.kakaoCode());
    }

    private record LockedContext(
            SocialAccount account,
            KakaoUnlinkTask task,
            User user,
            LocalDateTime leaseNow,
            String claimToken) {}

    private record LockedContextResult(LockedContext context, KakaoUnlinkProcessingResult outcome) {

        private static LockedContextResult context(LockedContext context) {
            return new LockedContextResult(context, null);
        }

        private static LockedContextResult outcome(KakaoUnlinkProcessingResult outcome) {
            return new LockedContextResult(null, outcome);
        }
    }
}
