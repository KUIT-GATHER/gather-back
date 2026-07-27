package com.gather.gather.domain.meeting.service;

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
        havingValue = "true")
public class MeetingImageCleanupScheduler {

    private final MeetingImageCleanupService meetingImageCleanupService;

    @Scheduled(fixedDelayString = "${gather.aws.s3.cleanup-fixed-delay-milliseconds}")
    public void run() {
        try {
            meetingImageCleanupService.deleteExpiredPendingUploads();
        } catch (Exception e) {
            log.warn("만료 미반영 모임 이미지 정리 실패", e);
        }
        try {
            meetingImageCleanupService.deleteSupersededObjects();
        } catch (Exception e) {
            log.warn("교체된 모임 이미지 정리 실패", e);
        }
    }
}
