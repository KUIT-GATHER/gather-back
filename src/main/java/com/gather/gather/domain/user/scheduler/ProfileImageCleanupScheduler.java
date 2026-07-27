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
        cleanupExpiredUploads();
        retryPreviousObjectDeletions();
    }

    private void cleanupExpiredUploads() {
        try {
            int expiredCount = profileImageCleanupService.cleanupExpiredUploads();
            if (expiredCount > 0) {
                log.info("만료된 프로필 이미지 업로드 정리 완료: expiredCount={}", expiredCount);
            }
        } catch (RuntimeException exception) {
            log.error("만료된 프로필 이미지 업로드 정리 실패", exception);
        }
    }

    private void retryPreviousObjectDeletions() {
        try {
            int previousCount = profileImageCleanupService.retryPreviousObjectDeletions();
            if (previousCount > 0) {
                log.info("기존 프로필 이미지 삭제 재시도 완료: previousCount={}", previousCount);
            }
        } catch (RuntimeException exception) {
            log.error("기존 프로필 이미지 삭제 재시도 실패", exception);
        }
    }
}
