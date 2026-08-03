package com.gather.gather.domain.notification.scheduler;

import com.gather.gather.domain.notification.service.BookmarkedPostingDeadlineNotificationService;
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
        prefix = "notification.bookmarked-posting-deadline",
        name = "scheduler-enabled",
        havingValue = "true")
public class BookmarkedPostingDeadlineNotificationScheduler {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final BookmarkedPostingDeadlineNotificationService notificationService;

    @Scheduled(cron = "${notification.bookmarked-posting-deadline.cron}", zone = "Asia/Seoul")
    public void createNotifications() {
        LocalDate today = LocalDate.now(KOREA_ZONE_ID);

        int createdCount = notificationService.createNotifications(today);

        log.info("북마크 공고 모집 마감 알림 생성을 완료했습니다. 기준일={}, 생성수={}", today, createdCount);
    }
}
