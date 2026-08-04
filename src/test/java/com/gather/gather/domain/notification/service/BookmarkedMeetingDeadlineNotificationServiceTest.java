package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.meeting.dto.BookmarkedMeetingDeadlineTarget;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BookmarkedMeetingDeadlineNotificationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 2);

    @Mock private MeetingBookmarkRepository meetingBookmarkRepository;

    @Mock private NotificationSettingRepository notificationSettingRepository;

    @Mock private NotificationWriter notificationWriter;

    private BookmarkedMeetingDeadlineNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService =
                new BookmarkedMeetingDeadlineNotificationService(
                        meetingBookmarkRepository,
                        notificationSettingRepository,
                        notificationWriter);
    }

    @Test
    void 북마크한_모임의_모집_마감이_3일_남으면_알림을_생성한다() {
        BookmarkedMeetingDeadlineTarget target =
                new BookmarkedMeetingDeadlineTarget(1L, 10L, "한강공원 플로깅");

        LocalDateTime start = LocalDateTime.of(2026, 8, 5, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 6, 0, 0);

        given(meetingBookmarkRepository.findMeetingDeadlineNotificationTargets(start, end))
                .willReturn(List.of(target));

        given(
                        notificationSettingRepository.findBookmarkedMeetingDeadlineEnabledUserIds(
                                List.of(1L)))
                .willReturn(List.of(1L));

        int result = notificationService.createNotifications(TODAY);

        assertThat(result).isEqualTo(1);

        verify(notificationWriter)
                .createScheduled(
                        1L,
                        NotificationType.MEETING_BOOKMARKED_DEADLINE,
                        "[한강공원 플로깅] 팀 모집 마감이 얼마 남지 않았어요. " + "참여를 고민 중이라면 지금 확인해 보세요.",
                        NotificationTargetType.MEETING,
                        10L,
                        "MEETING_BOOKMARKED_DEADLINE:1:10:2026-08-05");
    }

    @Test
    void 알림_설정을_비활성화한_사용자에게는_알림을_생성하지_않는다() {
        BookmarkedMeetingDeadlineTarget target =
                new BookmarkedMeetingDeadlineTarget(1L, 10L, "한강공원 플로깅");

        given(
                        meetingBookmarkRepository.findMeetingDeadlineNotificationTargets(
                                LocalDateTime.of(2026, 8, 5, 0, 0),
                                LocalDateTime.of(2026, 8, 6, 0, 0)))
                .willReturn(List.of(target));

        given(
                        notificationSettingRepository.findBookmarkedMeetingDeadlineEnabledUserIds(
                                List.of(1L)))
                .willReturn(List.of());

        int result = notificationService.createNotifications(TODAY);

        assertThat(result).isZero();
        verify(notificationWriter, never())
                .createScheduled(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 대상이_없으면_알림_설정과_알림_저장을_조회하지_않는다() {
        given(
                        meetingBookmarkRepository.findMeetingDeadlineNotificationTargets(
                                LocalDateTime.of(2026, 8, 5, 0, 0),
                                LocalDateTime.of(2026, 8, 6, 0, 0)))
                .willReturn(List.of());

        int result = notificationService.createNotifications(TODAY);

        assertThat(result).isZero();

        verify(notificationSettingRepository, never())
                .findBookmarkedMeetingDeadlineEnabledUserIds(
                        org.mockito.ArgumentMatchers.anyCollection());

        verify(notificationWriter, never())
                .createScheduled(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 중복된_알림은_실패로_처리하지_않는다() {
        BookmarkedMeetingDeadlineTarget target =
                new BookmarkedMeetingDeadlineTarget(1L, 10L, "한강공원 플로깅");

        given(
                        meetingBookmarkRepository.findMeetingDeadlineNotificationTargets(
                                LocalDateTime.of(2026, 8, 5, 0, 0),
                                LocalDateTime.of(2026, 8, 6, 0, 0)))
                .willReturn(List.of(target));

        given(
                        notificationSettingRepository.findBookmarkedMeetingDeadlineEnabledUserIds(
                                List.of(1L)))
                .willReturn(List.of(1L));

        DataIntegrityViolationException duplicateException =
                new DataIntegrityViolationException(
                        "duplicate",
                        new SQLException("uq_notification_deduplication_key", "23505"));

        org.mockito.Mockito.doThrow(duplicateException)
                .when(notificationWriter)
                .createScheduled(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());

        int result = notificationService.createNotifications(TODAY);

        assertThat(result).isZero();
    }

    @Test
    void 한_사용자의_알림_생성이_실패해도_다음_사용자의_알림을_시도한다() {
        BookmarkedMeetingDeadlineTarget firstTarget =
                new BookmarkedMeetingDeadlineTarget(1L, 10L, "첫 번째 모임");

        BookmarkedMeetingDeadlineTarget secondTarget =
                new BookmarkedMeetingDeadlineTarget(2L, 20L, "두 번째 모임");

        given(
                        meetingBookmarkRepository.findMeetingDeadlineNotificationTargets(
                                LocalDateTime.of(2026, 8, 5, 0, 0),
                                LocalDateTime.of(2026, 8, 6, 0, 0)))
                .willReturn(List.of(firstTarget, secondTarget));

        given(
                        notificationSettingRepository.findBookmarkedMeetingDeadlineEnabledUserIds(
                                List.of(1L, 2L)))
                .willReturn(List.of(1L, 2L));

        org.mockito.Mockito.doThrow(new RuntimeException("첫 번째 저장 실패"))
                .when(notificationWriter)
                .createScheduled(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> notificationService.createNotifications(TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1건이 실패");

        verify(notificationWriter)
                .createScheduled(
                        org.mockito.ArgumentMatchers.eq(2L),
                        org.mockito.ArgumentMatchers.eq(
                                NotificationType.MEETING_BOOKMARKED_DEADLINE),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(NotificationTargetType.MEETING),
                        org.mockito.ArgumentMatchers.eq(20L),
                        org.mockito.ArgumentMatchers.anyString());
    }
}
