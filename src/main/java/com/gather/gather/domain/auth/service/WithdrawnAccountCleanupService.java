package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.kakao.client.KakaoUnlinkResult;
import com.gather.gather.domain.auth.kakao.service.KakaoUnlinkService;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawnAccountCleanupService {

    private static final int BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final KakaoUnlinkService kakaoUnlinkService;
    private final WithdrawalPolicy withdrawalPolicy;

    @Transactional
    public int anonymizeExpiredAccounts() {
        List<User> targets =
                userRepository.findAnonymizationTargets(
                        UserStatus.WITHDRAWN,
                        withdrawalPolicy.graceExpiryThreshold(LocalDateTime.now()),
                        PageRequest.of(0, BATCH_SIZE));
        targets.forEach(User::anonymize);
        return targets.size();
    }

    public UnlinkRetrySummary retryPendingUnlinks() {
        List<SocialAccount> pending =
                socialAccountRepository.findByUserStatus(
                        UserStatus.WITHDRAWN, PageRequest.of(0, BATCH_SIZE));
        LocalDateTime now = LocalDateTime.now();
        int resolvedCount = 0;
        int noLinkedAccountCount = 0;
        int retryPendingCount = 0;
        int failedCount = 0;
        int forcedDeletionCount = 0;
        for (SocialAccount account : pending) {
            User user = account.getUser();
            try {
                if (isGraceExpired(user, now)) {
                    log.error(
                            "Kakao unlink was not confirmed before the grace period ended. userId={}",
                            user.getId());
                    socialAccountRepository.delete(account);
                    forcedDeletionCount++;
                    continue;
                }
                KakaoUnlinkResult result = kakaoUnlinkService.unlinkIfLinked(user.getId());
                switch (result) {
                    case SUCCESS, ALREADY_UNLINKED -> resolvedCount++;
                    case NOT_LINKED -> noLinkedAccountCount++;
                    case RETRYABLE_FAILURE -> retryPendingCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn("Kakao unlink retry failed. userId={}", user.getId(), exception);
            }
        }
        return new UnlinkRetrySummary(
                resolvedCount,
                noLinkedAccountCount,
                retryPendingCount,
                failedCount,
                forcedDeletionCount);
    }

    private boolean isGraceExpired(User user, LocalDateTime now) {
        return user.getWithdrawnAt() != null
                && withdrawalPolicy.isGracePeriodOver(user.getWithdrawnAt(), now);
    }
}
