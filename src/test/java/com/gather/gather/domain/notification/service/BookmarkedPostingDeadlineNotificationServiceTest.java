package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.BookmarkedPostingDeadlineTarget;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookmarkedPostingDeadlineNotificationServiceTest {

    @Mock private BookmarkRepository bookmarkRepository;

    @Mock private NotificationSettingRepository notificationSettingRepository;

    @Mock private NotificationWriter notificationWriter;

    private BookmarkedPostingDeadlineNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService =
                new BookmarkedPostingDeadlineNotificationService(
                        bookmarkRepository, notificationSettingRepository, notificationWriter);
    }

    @Test
    void 북마크한_공고의_모집_마감_3일_전에_알림을_생성한다() {
        LocalDate today = LocalDate.of(2026, 8, 2);
        LocalDate deadlineDate = LocalDate.of(2026, 8, 5);

        BookmarkedPostingDeadlineTarget target =
                new BookmarkedPostingDeadlineTarget(1L, 10L, "한강공원 플로깅");

        given(bookmarkRepository.findPostingDeadlineNotificationTargets(deadlineDate))
                .willReturn(List.of(target));
        given(
                        notificationSettingRepository.findBookmarkedPostingDeadlineEnabledUserIds(
                                anyCollection()))
                .willReturn(List.of(1L));

        int createdCount = notificationService.createNotifications(today);

        assertThat(createdCount).isEqualTo(1);

        verify(notificationWriter)
                .createScheduled(
                        eq(1L),
                        eq(NotificationType.BOOKMARKED_POSTING_DEADLINE),
                        eq("[한강공원 플로깅] 모집 마감이 얼마 남지 않았어요. " + "신청을 고민 중이라면 지금 확인해 보세요."),
                        eq(NotificationTargetType.POSTING),
                        eq(10L),
                        eq("BOOKMARKED_POSTING_DEADLINE:1:10:2026-08-05"));
    }

    @Test
    void 알림_설정을_켜지_않은_사용자의_알림은_생성하지_않는다() {
        LocalDate today = LocalDate.of(2026, 8, 2);

        BookmarkedPostingDeadlineTarget target =
                new BookmarkedPostingDeadlineTarget(1L, 10L, "한강공원 플로깅");

        given(bookmarkRepository.findPostingDeadlineNotificationTargets(today.plusDays(3)))
                .willReturn(List.of(target));
        given(
                        notificationSettingRepository.findBookmarkedPostingDeadlineEnabledUserIds(
                                anyCollection()))
                .willReturn(List.of());

        int createdCount = notificationService.createNotifications(today);

        assertThat(createdCount).isZero();
        verify(notificationWriter, never())
                .createScheduled(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 대상이_없으면_알림_설정과_저장소를_호출하지_않는다() {
        LocalDate today = LocalDate.of(2026, 8, 2);

        given(bookmarkRepository.findPostingDeadlineNotificationTargets(today.plusDays(3)))
                .willReturn(List.of());

        int createdCount = notificationService.createNotifications(today);

        assertThat(createdCount).isZero();
        verify(notificationSettingRepository, never())
                .findBookmarkedPostingDeadlineEnabledUserIds(anyCollection());
        verify(notificationWriter, never())
                .createScheduled(any(), any(), any(), any(), any(), any());
    }
}
