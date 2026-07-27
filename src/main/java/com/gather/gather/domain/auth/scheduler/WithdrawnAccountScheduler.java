package com.gather.gather.domain.auth.scheduler;

import com.gather.gather.domain.auth.service.WithdrawnAccountCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 계정 뒤처리 배치. ShedLock이 없어 단일 인스턴스 전제다(프로젝트 전체 관례와 동일).
 *
 * <p>두 작업을 각각 try/catch로 감싸 한쪽이 실패해도 다른 쪽이 실행되게 한다. 익명화가 하루 밀리면 개인정보가 하루 더 남고, 연결 해제 재시도가 밀리면 카카오
 * 연결이 하루 더 유지된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "gather.auth.withdrawal",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WithdrawnAccountScheduler {

    private final WithdrawnAccountCleanupService withdrawnAccountCleanupService;

    /** 매일 새벽 4시 30분(KST). 공고 정리(4시)와 겹치지 않게 뒤로 물렸다. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanupWithdrawnAccounts() {
        anonymizeExpiredAccounts();
        retryPendingUnlinks();
    }

    private void anonymizeExpiredAccounts() {
        try {
            int count = withdrawnAccountCleanupService.anonymizeExpiredAccounts();
            if (count > 0) {
                log.info("탈퇴 계정 익명화 완료. count={}", count);
            }
        } catch (RuntimeException exception) {
            log.error("탈퇴 계정 익명화 배치 실패", exception);
        }
    }

    private void retryPendingUnlinks() {
        try {
            int count = withdrawnAccountCleanupService.retryPendingUnlinks();
            if (count > 0) {
                log.info("카카오 연결 해제 재처리 완료. count={}", count);
            }
        } catch (RuntimeException exception) {
            log.error("카카오 연결 해제 재처리 배치 실패", exception);
        }
    }
}
