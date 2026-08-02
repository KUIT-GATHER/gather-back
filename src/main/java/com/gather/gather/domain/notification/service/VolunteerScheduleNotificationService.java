package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.VolunteerScheduleTarget;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
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
        int weekBeforeCount =
                createNotificationsFor(
                        today.plusDays(WEEK_BEFORE), WEEK_BEFORE, WEEK_BEFORE_MESSAGE);

        int dayBeforeCount =
                createNotificationsFor(today.plusDays(DAY_BEFORE), DAY_BEFORE, DAY_BEFORE_MESSAGE);

        return weekBeforeCount + dayBeforeCount;
    }

    private int createNotificationsFor(
            LocalDate activityDate, int daysBefore, String messageFormat) {

        List<VolunteerScheduleTarget> targets =
                postingParticipationRepository.findVolunteerScheduleTargets(activityDate);

        if (targets.isEmpty()) {
            return 0;
        }

        Set<Long> disabledUserIds =
                new HashSet<>(
                        notificationSettingRepository.findVolunteerScheduleDisabledUserIds(
                                targets.stream()
                                        .map(VolunteerScheduleTarget::userId)
                                        .distinct()
                                        .toList()));

        int successCount = 0;

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
                log.debug(
                        "이미 생성된 봉사 일정 알림입니다. userId={}, postingId={}, activityDate={}, daysBefore={}",
                        target.userId(),
                        target.postingId(),
                        activityDate,
                        daysBefore);
            } catch (RuntimeException exception) {
                log.warn(
                        "봉사 일정 알림 생성 실패. userId={}, postingId={}, activityDate={}, daysBefore={}",
                        target.userId(),
                        target.postingId(),
                        activityDate,
                        daysBefore,
                        exception);
            }
        }

        log.info(
                "봉사 일정 알림 생성 완료. activityDate={}, daysBefore={}, requestedCount={}, successCount={}",
                activityDate,
                daysBefore,
                targets.size(),
                successCount);

        return successCount;
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
}
