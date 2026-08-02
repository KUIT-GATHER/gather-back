package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.domain.posting.dto.BookmarkedPostingDeadlineTarget;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
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
public class BookmarkedPostingDeadlineNotificationService {

    private static final int DEADLINE_NOTICE_DAYS = 3;
    private static final int MAX_TITLE_LENGTH = 170;

    private static final String MESSAGE_FORMAT = "[%s] 모집 마감이 얼마 남지 않았어요. 신청을 고민 중이라면 지금 확인해 보세요.";

    private static final String DEDUPLICATION_KEY_FORMAT = "BOOKMARKED_POSTING_DEADLINE:%d:%d:%s";

    private final BookmarkRepository bookmarkRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationWriter notificationWriter;

    public int createNotifications(LocalDate today) {
        LocalDate deadlineDate = today.plusDays(DEADLINE_NOTICE_DAYS);

        List<BookmarkedPostingDeadlineTarget> targets =
                bookmarkRepository.findPostingDeadlineNotificationTargets(deadlineDate);

        if (targets.isEmpty()) {
            return 0;
        }

        Set<Long> enabledUserIds = findEnabledUserIds(targets);

        int successCount = 0;
        int failureCount = 0;

        for (BookmarkedPostingDeadlineTarget target : targets) {
            if (!enabledUserIds.contains(target.userId())) {
                continue;
            }

            NotificationCreationResult result = createNotification(target, deadlineDate);

            if (result == NotificationCreationResult.CREATED) {
                successCount++;
            } else if (result == NotificationCreationResult.FAILED) {
                failureCount++;
            }
        }

        log.info(
                "북마크 공고 마감 알림 생성 완료. deadlineDate={}, requestedCount={}, "
                        + "successCount={}, failureCount={}",
                deadlineDate,
                targets.size(),
                successCount,
                failureCount);

        if (failureCount > 0) {
            throw new IllegalStateException(
                    "북마크 공고 마감 알림 생성 중 %d건이 실패했습니다.".formatted(failureCount));
        }

        return successCount;
    }

    private Set<Long> findEnabledUserIds(List<BookmarkedPostingDeadlineTarget> targets) {
        Set<Long> targetUserIds = new HashSet<>();

        for (BookmarkedPostingDeadlineTarget target : targets) {
            targetUserIds.add(target.userId());
        }

        return new HashSet<>(
                notificationSettingRepository.findBookmarkedPostingDeadlineEnabledUserIds(
                        targetUserIds));
    }

    private NotificationCreationResult createNotification(
            BookmarkedPostingDeadlineTarget target, LocalDate deadlineDate) {

        String message = MESSAGE_FORMAT.formatted(abbreviateTitle(target.postingTitle()));
        String deduplicationKey =
                DEDUPLICATION_KEY_FORMAT.formatted(
                        target.userId(), target.postingId(), deadlineDate);

        try {
            notificationWriter.createScheduled(
                    target.userId(),
                    NotificationType.BOOKMARKED_POSTING_DEADLINE,
                    message,
                    NotificationTargetType.POSTING,
                    target.postingId(),
                    deduplicationKey);

            return NotificationCreationResult.CREATED;
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateNotification(exception)) {
                log.debug(
                        "이미 생성된 북마크 공고 마감 알림입니다. " + "userId={}, postingId={}, deadlineDate={}",
                        target.userId(),
                        target.postingId(),
                        deadlineDate);

                return NotificationCreationResult.DUPLICATE;
            }

            log.error(
                    "북마크 공고 마감 알림 저장 무결성 오류. " + "userId={}, postingId={}, deadlineDate={}",
                    target.userId(),
                    target.postingId(),
                    deadlineDate,
                    exception);

            return NotificationCreationResult.FAILED;
        } catch (RuntimeException exception) {
            log.error(
                    "북마크 공고 마감 알림 생성 실패. " + "userId={}, postingId={}, deadlineDate={}",
                    target.userId(),
                    target.postingId(),
                    deadlineDate,
                    exception);

            return NotificationCreationResult.FAILED;
        }
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

    private String abbreviateTitle(String title) {
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }

        return title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
    }

    private enum NotificationCreationResult {
        CREATED,
        DUPLICATE,
        FAILED
    }
}
