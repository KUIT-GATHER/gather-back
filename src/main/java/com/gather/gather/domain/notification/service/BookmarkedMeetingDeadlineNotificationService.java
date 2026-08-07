package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.meeting.dto.BookmarkedMeetingDeadlineTarget;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkedMeetingDeadlineNotificationService {

    private static final int DAYS_BEFORE_DEADLINE = 3;

    private static final String MESSAGE_FORMAT =
            "[%s] 팀 모집 마감이 얼마 남지 않았어요. 참여를 고민 중이라면 지금 확인해 보세요.";

    private static final String DEDUPLICATION_KEY_FORMAT = "MEETING_BOOKMARKED_DEADLINE:%d:%d:%s";
    private static final String DEDUPLICATION_CONSTRAINT = "uq_notification_deduplication_key";

    private final MeetingBookmarkRepository meetingBookmarkRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationWriter notificationWriter;

    public int createNotifications(LocalDate today) {
        LocalDate deadlineDate = today.plusDays(DAYS_BEFORE_DEADLINE);
        LocalDateTime deadlineStartInclusive = deadlineDate.atStartOfDay();
        LocalDateTime deadlineEndExclusive = deadlineDate.plusDays(1).atStartOfDay();

        List<BookmarkedMeetingDeadlineTarget> targets =
                meetingBookmarkRepository.findMeetingDeadlineNotificationTargets(
                        deadlineStartInclusive, deadlineEndExclusive);

        if (targets.isEmpty()) {
            return 0;
        }

        Set<Long> enabledUserIds =
                new HashSet<>(
                        notificationSettingRepository.findBookmarkedMeetingDeadlineEnabledUserIds(
                                targets.stream()
                                        .map(BookmarkedMeetingDeadlineTarget::userId)
                                        .distinct()
                                        .toList()));

        int successCount = 0;
        int failureCount = 0;

        for (BookmarkedMeetingDeadlineTarget target : targets) {
            if (!enabledUserIds.contains(target.userId())) {
                continue;
            }

            String message = MESSAGE_FORMAT.formatted(target.meetingName());
            String deduplicationKey =
                    DEDUPLICATION_KEY_FORMAT.formatted(
                            target.userId(), target.meetingId(), deadlineDate);

            try {
                notificationWriter.createScheduled(
                        target.userId(),
                        NotificationType.MEETING_BOOKMARKED_DEADLINE,
                        message,
                        NotificationTargetType.MEETING,
                        target.meetingId(),
                        deduplicationKey);

                successCount++;
            } catch (DataIntegrityViolationException exception) {
                if (isDuplicateNotification(exception)) {
                    log.debug(
                            "북마크 모임 모집 마감 알림 중복 생성을 건너뜁니다. "
                                    + "userId={}, meetingId={}, deadlineDate={}",
                            target.userId(),
                            target.meetingId(),
                            deadlineDate);
                    continue;
                }

                failureCount++;
                log.error(
                        "북마크 모임 모집 마감 알림 저장에 실패했습니다. " + "userId={}, meetingId={}, deadlineDate={}",
                        target.userId(),
                        target.meetingId(),
                        deadlineDate,
                        exception);
            } catch (RuntimeException exception) {
                failureCount++;
                log.error(
                        "북마크 모임 모집 마감 알림 생성에 실패했습니다. " + "userId={}, meetingId={}, deadlineDate={}",
                        target.userId(),
                        target.meetingId(),
                        deadlineDate,
                        exception);
            }
        }

        log.info(
                "북마크 모임 모집 마감 알림 생성을 완료했습니다. "
                        + "deadlineDate={}, targetCount={}, successCount={}, failureCount={}",
                deadlineDate,
                targets.size(),
                successCount,
                failureCount);

        if (failureCount > 0) {
            throw new IllegalStateException("북마크 모임 모집 마감 알림 생성 중 " + failureCount + "건이 실패했습니다.");
        }

        return successCount;
    }

    private boolean isDuplicateNotification(DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {
            String message = cause.getMessage();

            if (message != null
                    && message.toLowerCase(Locale.ROOT).contains(DEDUPLICATION_CONSTRAINT)) {
                return true;
            }

            if (cause instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();

                if ("23505".equals(sqlState)
                        && message != null
                        && message.toLowerCase(Locale.ROOT).contains(DEDUPLICATION_CONSTRAINT)) {
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }
}
