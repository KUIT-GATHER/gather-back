package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.domain.posting.dto.BookmarkedPostingDeadlineTarget;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

    @Test
    void 중복_알림은_건너뛰고_다음_사용자의_알림을_생성한다() {
        LocalDate today = LocalDate.of(2026, 8, 2);

        BookmarkedPostingDeadlineTarget duplicateTarget =
                new BookmarkedPostingDeadlineTarget(1L, 10L, "중복 공고");
        BookmarkedPostingDeadlineTarget normalTarget =
                new BookmarkedPostingDeadlineTarget(2L, 20L, "정상 공고");

        given(bookmarkRepository.findPostingDeadlineNotificationTargets(today.plusDays(3)))
                .willReturn(List.of(duplicateTarget, normalTarget));
        given(
                        notificationSettingRepository.findBookmarkedPostingDeadlineEnabledUserIds(
                                anyCollection()))
                .willReturn(List.of(1L, 2L));

        given(notificationWriter.createScheduled(eq(1L), any(), any(), any(), any(), any()))
                .willThrow(
                        new DataIntegrityViolationException(
                                "duplicate",
                                new SQLException(
                                        "Duplicate entry for key "
                                                + "'uq_notification_deduplication_key'")));

        int createdCount = notificationService.createNotifications(today);

        assertThat(createdCount).isEqualTo(1);

        verify(notificationWriter).createScheduled(eq(2L), any(), any(), any(), eq(20L), any());
    }

    @Test
    void 일반_저장_실패가_발생해도_다음_사용자까지_처리하고_작업을_실패시킨다() {
        LocalDate today = LocalDate.of(2026, 8, 2);

        BookmarkedPostingDeadlineTarget failedTarget =
                new BookmarkedPostingDeadlineTarget(1L, 10L, "실패 공고");
        BookmarkedPostingDeadlineTarget normalTarget =
                new BookmarkedPostingDeadlineTarget(2L, 20L, "정상 공고");

        given(bookmarkRepository.findPostingDeadlineNotificationTargets(today.plusDays(3)))
                .willReturn(List.of(failedTarget, normalTarget));
        given(
                        notificationSettingRepository.findBookmarkedPostingDeadlineEnabledUserIds(
                                anyCollection()))
                .willReturn(List.of(1L, 2L));

        given(notificationWriter.createScheduled(eq(1L), any(), any(), any(), any(), any()))
                .willThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> notificationService.createNotifications(today))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1건");

        verify(notificationWriter).createScheduled(eq(2L), any(), any(), any(), eq(20L), any());
    }

    @Test
    void 중복키가_아닌_무결성_오류는_작업_실패로_보고한다() {
        LocalDate today = LocalDate.of(2026, 8, 2);

        BookmarkedPostingDeadlineTarget target =
                new BookmarkedPostingDeadlineTarget(1L, 10L, "한강공원 플로깅");

        given(bookmarkRepository.findPostingDeadlineNotificationTargets(today.plusDays(3)))
                .willReturn(List.of(target));
        given(
                        notificationSettingRepository.findBookmarkedPostingDeadlineEnabledUserIds(
                                anyCollection()))
                .willReturn(List.of(1L));

        given(notificationWriter.createScheduled(any(), any(), any(), any(), any(), any()))
                .willThrow(new DataIntegrityViolationException("foreign key violation"));

        assertThatThrownBy(() -> notificationService.createNotifications(today))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1건");
    }

    @Test
    void 긴_공고명은_말줄임표를_포함해_170자로_축약한다() {
        LocalDate today = LocalDate.of(2026, 8, 2);
        String longTitle = "가".repeat(171);

        BookmarkedPostingDeadlineTarget target =
                new BookmarkedPostingDeadlineTarget(1L, 10L, longTitle);

        given(bookmarkRepository.findPostingDeadlineNotificationTargets(today.plusDays(3)))
                .willReturn(List.of(target));
        given(
                        notificationSettingRepository.findBookmarkedPostingDeadlineEnabledUserIds(
                                anyCollection()))
                .willReturn(List.of(1L));

        notificationService.createNotifications(today);

        verify(notificationWriter)
                .createScheduled(
                        eq(1L),
                        eq(NotificationType.BOOKMARKED_POSTING_DEADLINE),
                        argThat(message -> message.startsWith("[" + "가".repeat(169) + "…]")),
                        eq(NotificationTargetType.POSTING),
                        eq(10L),
                        any());
    }
}
