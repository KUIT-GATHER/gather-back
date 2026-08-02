package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.BookmarkedPostingDeadlineTarget;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
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

        int createdCount = 0;

        for (BookmarkedPostingDeadlineTarget target : targets) {
            if (!enabledUserIds.contains(target.userId())) {
                continue;
            }

            if (createNotification(target, deadlineDate)) {
                createdCount++;
            }
        }

        return createdCount;
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

    private boolean createNotification(
            BookmarkedPostingDeadlineTarget target, LocalDate deadlineDate) {
        String postingTitle = truncate(target.postingTitle());
        String message = MESSAGE_FORMAT.formatted(postingTitle);
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

            return true;
        } catch (DataIntegrityViolationException exception) {
            log.debug(
                    "북마크 공고 마감 알림이 이미 생성되어 건너뜁니다. userId={}, postingId={}, deadlineDate={}",
                    target.userId(),
                    target.postingId(),
                    deadlineDate);

            return false;
        } catch (RuntimeException exception) {
            log.warn(
                    "북마크 공고 마감 알림 생성에 실패했습니다. userId={}, postingId={}, deadlineDate={}",
                    target.userId(),
                    target.postingId(),
                    deadlineDate,
                    exception);

            return false;
        }
    }

    private String truncate(String title) {
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }

        return title.substring(0, MAX_TITLE_LENGTH);
    }
}
