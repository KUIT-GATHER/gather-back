package com.gather.gather.domain.notification.scheduler;

import com.gather.gather.domain.notification.service.BookmarkedMeetingDeadlineNotificationService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "notification.bookmarked-meeting-deadline",
        name = "scheduler-enabled",
        havingValue = "true")
public class BookmarkedMeetingDeadlineNotificationScheduler {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final BookmarkedMeetingDeadlineNotificationService notificationService;

    @Scheduled(cron = "${notification.bookmarked-meeting-deadline.cron}", zone = "Asia/Seoul")
    public void createNotifications() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);

        log.info("북마크 모임 모집 마감 알림 스케줄러를 시작합니다. today={}", today);

        int createdCount = notificationService.createNotifications(today);

        log.info(
                "북마크 모임 모집 마감 알림 스케줄러를 완료했습니다. " + "today={}, createdCount={}",
                today,
                createdCount);
    }
}
