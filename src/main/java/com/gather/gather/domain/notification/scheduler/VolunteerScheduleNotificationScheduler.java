package com.gather.gather.domain.notification.scheduler;

import com.gather.gather.domain.notification.service.VolunteerScheduleNotificationService;
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
        prefix = "notification.volunteer-schedule",
        name = "scheduler-enabled",
        havingValue = "true")
public class VolunteerScheduleNotificationScheduler {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final VolunteerScheduleNotificationService volunteerScheduleNotificationService;

    @Scheduled(cron = "${notification.volunteer-schedule.cron}", zone = "Asia/Seoul")
    public void createNotifications() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);

        int count = volunteerScheduleNotificationService.createNotifications(today);

        log.info("봉사 일정 알림 스케줄러 완료. today={}, count={}", today, count);
    }
}
