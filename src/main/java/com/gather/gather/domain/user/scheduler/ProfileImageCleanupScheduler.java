package com.gather.gather.domain.user.scheduler;

import com.gather.gather.domain.user.service.ProfileImageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "gather.aws.s3",
        name = "cleanup-scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ProfileImageCleanupScheduler {

    private final ProfileImageCleanupService profileImageCleanupService;

    @Scheduled(fixedDelayString = "${gather.aws.s3.cleanup-fixed-delay-milliseconds:3600000}")
    public void cleanupProfileImages() {
        try {
            int expiredCount = profileImageCleanupService.cleanupExpiredUploads();
            int previousCount = profileImageCleanupService.retryPreviousObjectDeletions();
            if (expiredCount > 0 || previousCount > 0) {
                log.info(
                        "프로필 이미지 객체 정리 완료: expiredCount={}, previousCount={}",
                        expiredCount,
                        previousCount);
            }
        } catch (RuntimeException exception) {
            log.error("프로필 이미지 객체 정리 배치 실패", exception);
        }
    }
}
