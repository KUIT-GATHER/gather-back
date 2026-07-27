package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
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

/** 탈퇴 계정의 뒤처리. 유예가 끝난 계정을 익명화하고, 실패해 남아 있는 카카오 연결 해제를 다시 시도한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawnAccountCleanupService {

    // 한 번에 처리할 양. 유예가 7일이라 하루 배치가 밀려도 다음 회차가 이어받으면 된다.
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

    /**
     * 트랜잭션을 걸지 않는다. 카카오 호출이 포함돼 있어 한 트랜잭션으로 묶으면 배치 전체가 커넥션을 오래 붙들고, 한 건이 실패할 때 이미 처리한 건까지 되돌아간다.
     */
    public int retryPendingUnlinks() {
        List<SocialAccount> pending =
                socialAccountRepository.findByUserStatus(
                        UserStatus.WITHDRAWN, PageRequest.of(0, BATCH_SIZE));

        LocalDateTime now = LocalDateTime.now();
        int processedCount = 0;
        for (SocialAccount account : pending) {
            User user = account.getUser();
            try {
                if (isGraceExpired(user, now)) {
                    // 카카오 쪽 연결은 남은 채 우리 기록만 사라지는 상태가 된다. 정합성이 어긋나므로 추적 가능해야 한다.
                    log.error("유예 기간이 지나도 카카오 연결 해제가 되지 않아 연동 정보만 삭제합니다. userId={}", user.getId());
                    socialAccountRepository.delete(account);
                } else {
                    kakaoUnlinkService.unlinkIfLinked(user.getId());
                }
                processedCount++;
            } catch (RuntimeException exception) {
                // 한 건의 실패가 나머지를 막지 않아야 한다. 남은 row는 다음 회차가 다시 집는다.
                log.warn("카카오 연결 해제 재시도에 실패했습니다. userId={}", user.getId(), exception);
            }
        }
        return processedCount;
    }

    private boolean isGraceExpired(User user, LocalDateTime now) {
        // withdrawnAt이 없는 과거 탈퇴 계정은 유예 시작 시점을 알 수 없어 강제 삭제하지 않고 재시도만 한다.
        return user.getWithdrawnAt() != null
                && withdrawalPolicy.isGracePeriodOver(user.getWithdrawnAt(), now);
    }
}
