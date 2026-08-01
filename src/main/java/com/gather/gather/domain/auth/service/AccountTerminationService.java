package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;
import com.gather.gather.domain.auth.entity.KakaoUnlinkTask;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.entity.WithdrawalReason;
import com.gather.gather.domain.auth.kakao.service.LockedPendingSocialSignupSessions;
import com.gather.gather.domain.auth.kakao.service.SocialSignupSessionService;
import com.gather.gather.domain.auth.repository.EmailVerificationRepository;
import com.gather.gather.domain.auth.repository.KakaoUnlinkTaskRepository;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import com.gather.gather.domain.auth.repository.SocialAccountIdentitySnapshot;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.user.service.ProfileImageDeletionService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountTerminationService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialSignupSessionService signupSessionService;
    private final AccountRejoinBlockService rejoinBlockService;
    private final AccountIdentityGuardService identityGuardService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final KakaoUnlinkTaskRepository unlinkTaskRepository;
    private final ProfileImageDeletionService profileImageDeletionService;
    private final Clock clock;

    /**
     * 외부 unlink 호출 없이 탈퇴를 완료하거나 durable task와 함께 접수한다.
     *
     * <p>중복 요청은 최초 완료 또는 접수 시각을 반환하며 block, task, 세션 변경, 개인정보 정리를 다시 수행하지 않는다. 한 요청의 모든 상태 전이와 만료
     * 시각이 같은 기준 시각을 사용하도록 현재 시각은 이 진입점에서 한 번만 계산한다.
     */
    @Transactional
    public AccountTerminationResult terminate(Long userId, WithdrawalReason reason) {
        LocalDateTime now = LocalDateTime.now(clock);
        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<SocialAccountIdentitySnapshot> snapshots =
                socialAccountRepository.findIdentitySnapshotsByUserIdAndProvider(
                        userId, SocialProvider.KAKAO);
        if (snapshots.size() > 1) {
            throw stateConflict();
        }

        SocialAccountIdentitySnapshot snapshot = snapshots.isEmpty() ? null : snapshots.get(0);
        if (user.getStatus() == UserStatus.WITHDRAWAL_PENDING) {
            return acceptedExisting(user, snapshot);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            return completedExisting(user, snapshot);
        }
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.SUSPENDED) {
            throw stateConflict();
        }

        if (snapshot == null) {
            return terminateLocalAccount(user, reason, now);
        }
        if (snapshot.linkStatus() != SocialAccountLinkStatus.LINKED) {
            throw stateConflict();
        }
        return acceptKakaoAccount(user, snapshot, reason, now);
    }

    private AccountTerminationResult terminateLocalAccount(
            User user, WithdrawalReason reason, LocalDateTime now) {
        String phoneNumber = user.getPhoneNumber();
        String email = user.getEmail();
        String profileImageKey = user.getProfileImageKey();

        RejoinBlockIdentifier phoneIdentifier = identityGuardService.lockPhone(phoneNumber, now);
        rejoinBlockService.createOrExtendBlock(phoneIdentifier, user.getId(), now);
        refreshTokenRepository.deleteAllByUserId(user.getId());
        if (email != null) {
            emailVerificationRepository.deleteAllByEmail(email);
        }
        profileImageDeletionService.scheduleDeletion(user.getId(), profileImageKey, now);

        user.withdraw(reason, now);
        user.anonymize(now);
        return new AccountTerminationResult(
                AccountTerminationOutcome.COMPLETED, user.getWithdrawnAt());
    }

    private AccountTerminationResult acceptKakaoAccount(
            User user,
            SocialAccountIdentitySnapshot snapshot,
            WithdrawalReason reason,
            LocalDateTime now) {
        RejoinBlockIdentifier kakaoIdentifier = requireKakaoIdentifier(snapshot);
        LockedPendingSocialSignupSessions lockedSignupSessions =
                signupSessionService.lockPendingForIdentity(
                        snapshot.provider(), kakaoIdentifier, now);

        SocialAccount socialAccount =
                socialAccountRepository
                        .findByIdForUpdate(snapshot.id())
                        .orElseThrow(this::stateConflict);
        validateLockedSocialAccount(user, snapshot, socialAccount);
        RejoinBlockIdentifier phoneIdentifier =
                identityGuardService.lockPhone(user.getPhoneNumber(), now);

        lockedSignupSessions.cancelAll(now);
        user.requestWithdrawal(reason, now);
        socialAccount.markUnlinkPending(now);
        rejoinBlockService.createOrExtendBlock(phoneIdentifier, user.getId(), now);
        rejoinBlockService.createOrExtendBlock(kakaoIdentifier, user.getId(), now);
        refreshTokenRepository.deleteAllByUserId(user.getId());

        KakaoUnlinkTask task =
                unlinkTaskRepository.saveAndFlush(
                        KakaoUnlinkTask.pending(socialAccount, snapshot.generation(), now));
        return new AccountTerminationResult(
                AccountTerminationOutcome.ACCEPTED, task.getCreatedAt());
    }

    private AccountTerminationResult acceptedExisting(
            User user, SocialAccountIdentitySnapshot snapshot) {
        if (snapshot == null
                || snapshot.linkStatus() != SocialAccountLinkStatus.UNLINK_PENDING
                || snapshot.generation() == null
                || snapshot.generation() <= 0) {
            throw stateConflict();
        }
        KakaoUnlinkTask task =
                unlinkTaskRepository
                        .findBySocialAccountIdAndGeneration(snapshot.id(), snapshot.generation())
                        .orElseThrow(this::stateConflict);
        SocialAccount taskAccount = task.getSocialAccount();
        if (taskAccount == null
                || taskAccount.getId() == null
                || !taskAccount.getId().equals(snapshot.id())
                || taskAccount.getUser() == null
                || !taskAccount.getUser().getId().equals(user.getId())
                || task.getGeneration() != snapshot.generation()) {
            throw stateConflict();
        }
        return new AccountTerminationResult(
                AccountTerminationOutcome.ACCEPTED, task.getCreatedAt());
    }

    private AccountTerminationResult completedExisting(
            User user, SocialAccountIdentitySnapshot snapshot) {
        if (snapshot != null && snapshot.linkStatus() != SocialAccountLinkStatus.UNLINKED) {
            throw stateConflict();
        }
        if (user.getWithdrawnAt() == null) {
            throw stateConflict();
        }
        return new AccountTerminationResult(
                AccountTerminationOutcome.COMPLETED, user.getWithdrawnAt());
    }

    private RejoinBlockIdentifier requireKakaoIdentifier(SocialAccountIdentitySnapshot snapshot) {
        if (snapshot.provider() != SocialProvider.KAKAO
                || snapshot.providerUserKey() == null
                || snapshot.providerUserKey().isBlank()
                || snapshot.providerUserKeyVersion() == null
                || snapshot.providerUserKeyVersion() <= 0
                || snapshot.generation() == null
                || snapshot.generation() <= 0) {
            throw stateConflict();
        }
        return new RejoinBlockIdentifier(
                AccountRejoinBlockIdentifierType.KAKAO,
                snapshot.providerUserKey(),
                snapshot.providerUserKeyVersion());
    }

    private void validateLockedSocialAccount(
            User user, SocialAccountIdentitySnapshot snapshot, SocialAccount socialAccount) {
        if (!socialAccount.getUser().getId().equals(user.getId())
                || socialAccount.getProvider() != SocialProvider.KAKAO
                || !socialAccount.isLinked()
                || !socialAccount.matchesGeneration(snapshot.generation())
                || !socialAccount.matchesProviderUserKey(
                        snapshot.providerUserKey(), snapshot.providerUserKeyVersion())) {
            throw stateConflict();
        }
    }

    private BusinessException stateConflict() {
        return new BusinessException(ErrorCode.ACCOUNT_TERMINATION_STATE_CONFLICT);
    }
}
