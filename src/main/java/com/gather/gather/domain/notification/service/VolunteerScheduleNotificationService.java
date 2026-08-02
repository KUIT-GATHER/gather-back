package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.domain.posting.dto.VolunteerScheduleTarget;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VolunteerScheduleNotificationService {

    private static final int WEEK_BEFORE = 7;
    private static final int DAY_BEFORE = 1;
    private static final int MAX_TITLE_LENGTH = 170;

    private static final String WEEK_BEFORE_MESSAGE = "[%s] 봉사 일정이 일주일 남았어요. 시간과 장소를 확인해 주세요.";

    private static final String DAY_BEFORE_MESSAGE = "[%s] 봉사가 내일 진행돼요. 시간과 장소를 확인해 주세요.";

    private final PostingParticipationRepository postingParticipationRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationWriter notificationWriter;

    public int createNotifications(LocalDate today) {
        NotificationBatchResult weekBeforeResult =
                createNotificationsFor(
                        today.plusDays(WEEK_BEFORE), WEEK_BEFORE, WEEK_BEFORE_MESSAGE);

        NotificationBatchResult dayBeforeResult =
                createNotificationsFor(today.plusDays(DAY_BEFORE), DAY_BEFORE, DAY_BEFORE_MESSAGE);

        int failureCount = weekBeforeResult.failureCount() + dayBeforeResult.failureCount();
        if (failureCount > 0) {
            throw new IllegalStateException("봉사 일정 알림 생성 중 %d건이 실패했습니다.".formatted(failureCount));
        }

        return weekBeforeResult.successCount() + dayBeforeResult.successCount();
    }

    private NotificationBatchResult createNotificationsFor(
            LocalDate activityDate, int daysBefore, String messageFormat) {

        List<VolunteerScheduleTarget> targets =
                postingParticipationRepository.findVolunteerScheduleTargets(activityDate);

        if (targets.isEmpty()) {
            return NotificationBatchResult.EMPTY;
        }

        Set<Long> disabledUserIds =
                new HashSet<>(
                        notificationSettingRepository.findVolunteerScheduleDisabledUserIds(
                                targets.stream()
                                        .map(VolunteerScheduleTarget::userId)
                                        .distinct()
                                        .toList()));

        int successCount = 0;
        int failureCount = 0;

        for (VolunteerScheduleTarget target : targets) {
            if (disabledUserIds.contains(target.userId())) {
                continue;
            }

            String message = messageFormat.formatted(abbreviateTitle(target.postingTitle()));

            String deduplicationKey = createDeduplicationKey(target, activityDate, daysBefore);

            try {
                notificationWriter.createScheduled(
                        target.userId(),
                        NotificationType.VOLUNTEER_SCHEDULE,
                        message,
                        NotificationTargetType.POSTING,
                        target.postingId(),
                        deduplicationKey);

                successCount++;
            } catch (DataIntegrityViolationException exception) {
                if (isDuplicateNotification(exception)) {
                    log.debug(
                            "이미 생성된 봉사 일정 알림입니다. userId={}, postingId={}, activityDate={}, daysBefore={}",
                            target.userId(),
                            target.postingId(),
                            activityDate,
                            daysBefore);
                } else {
                    failureCount++;
                    log.error(
                            "봉사 일정 알림 저장 무결성 오류. userId={}, postingId={}, activityDate={}, daysBefore={}",
                            target.userId(),
                            target.postingId(),
                            activityDate,
                            daysBefore,
                            exception);
                }
            } catch (RuntimeException exception) {
                failureCount++;
                log.error(
                        "봉사 일정 알림 생성 실패. userId={}, postingId={}, activityDate={}, daysBefore={}",
                        target.userId(),
                        target.postingId(),
                        activityDate,
                        daysBefore,
                        exception);
            }
        }

        log.info(
                "봉사 일정 알림 생성 완료. activityDate={}, daysBefore={}, requestedCount={}, successCount={}, failureCount={}",
                activityDate,
                daysBefore,
                targets.size(),
                successCount,
                failureCount);

        return new NotificationBatchResult(successCount, failureCount);
    }

    private boolean isDuplicateNotification(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            String message = cause.getMessage();
            if (message != null
                    && message.toLowerCase().contains("uq_notification_deduplication_key")) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }

    private String createDeduplicationKey(
            VolunteerScheduleTarget target, LocalDate activityDate, int daysBefore) {

        return "VOLUNTEER_SCHEDULE:%d:%d:%s:%d"
                .formatted(target.userId(), target.postingId(), activityDate, daysBefore);
    }

    private String abbreviateTitle(String title) {
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }

        return title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
    }

    private record NotificationBatchResult(int successCount, int failureCount) {

        private static final NotificationBatchResult EMPTY = new NotificationBatchResult(0, 0);
    }
}
