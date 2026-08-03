package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.domain.posting.dto.VolunteerScheduleTarget;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
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
class VolunteerScheduleNotificationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 2);

    @Mock private PostingParticipationRepository postingParticipationRepository;

    @Mock private NotificationSettingRepository notificationSettingRepository;

    @Mock private NotificationWriter notificationWriter;

    private VolunteerScheduleNotificationService service;

    @BeforeEach
    void setUp() {
        service =
                new VolunteerScheduleNotificationService(
                        postingParticipationRepository,
                        notificationSettingRepository,
                        notificationWriter);
    }

    @Test
    void 봉사_시작_7일_전_일정_알림을_생성한다() {
        VolunteerScheduleTarget target = target(1L, 10L, "한강공원 플로깅");
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(7)))
                .willReturn(List.of(target));
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(1)))
                .willReturn(List.of());
        given(notificationSettingRepository.findVolunteerScheduleDisabledUserIds(List.of(1L)))
                .willReturn(List.of());

        int count = service.createNotifications(TODAY);

        assertThat(count).isEqualTo(1);
        verify(notificationWriter)
                .createScheduled(
                        1L,
                        NotificationType.VOLUNTEER_SCHEDULE,
                        "[한강공원 플로깅] 봉사 일정이 일주일 남았어요. 시간과 장소를 확인해 주세요.",
                        NotificationTargetType.POSTING,
                        10L,
                        "VOLUNTEER_SCHEDULE:1:10:2026-08-09:7");
    }

    @Test
    void 봉사_시작_1일_전_일정_알림을_생성한다() {
        VolunteerScheduleTarget target = target(1L, 10L, "한강공원 플로깅");
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(7)))
                .willReturn(List.of());
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(1)))
                .willReturn(List.of(target));
        given(notificationSettingRepository.findVolunteerScheduleDisabledUserIds(List.of(1L)))
                .willReturn(List.of());

        int count = service.createNotifications(TODAY);

        assertThat(count).isEqualTo(1);
        verify(notificationWriter)
                .createScheduled(
                        1L,
                        NotificationType.VOLUNTEER_SCHEDULE,
                        "[한강공원 플로깅] 봉사가 내일 진행돼요. 시간과 장소를 확인해 주세요.",
                        NotificationTargetType.POSTING,
                        10L,
                        "VOLUNTEER_SCHEDULE:1:10:2026-08-03:1");
    }

    @Test
    void 봉사_일정_알림을_끈_사용자는_제외한다() {
        VolunteerScheduleTarget target = target(1L, 10L, "한강공원 플로깅");
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(7)))
                .willReturn(List.of(target));
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(1)))
                .willReturn(List.of());
        given(notificationSettingRepository.findVolunteerScheduleDisabledUserIds(List.of(1L)))
                .willReturn(List.of(1L));

        int count = service.createNotifications(TODAY);

        assertThat(count).isZero();
        verify(notificationWriter, never())
                .createScheduled(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 중복_알림은_건너뛰고_다음_사용자_알림을_생성한다() {
        VolunteerScheduleTarget duplicateTarget = target(1L, 10L, "중복 공고");
        VolunteerScheduleTarget normalTarget = target(2L, 20L, "정상 공고");
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(7)))
                .willReturn(List.of(duplicateTarget, normalTarget));
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(1)))
                .willReturn(List.of());
        given(notificationSettingRepository.findVolunteerScheduleDisabledUserIds(List.of(1L, 2L)))
                .willReturn(List.of());
        given(notificationWriter.createScheduled(eq(1L), any(), any(), any(), any(), any()))
                .willThrow(
                        new DataIntegrityViolationException(
                                "duplicate",
                                new SQLException(
                                        "Duplicate entry for key 'uq_notification_deduplication_key'")));

        int count = service.createNotifications(TODAY);

        assertThat(count).isEqualTo(1);
        verify(notificationWriter).createScheduled(eq(2L), any(), any(), any(), eq(20L), any());
    }

    @Test
    void 일반_저장_실패가_발생해도_다음_사용자까지_처리하고_작업을_실패시킨다() {
        VolunteerScheduleTarget failedTarget = target(1L, 10L, "실패 공고");
        VolunteerScheduleTarget normalTarget = target(2L, 20L, "정상 공고");
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(7)))
                .willReturn(List.of(failedTarget, normalTarget));
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(1)))
                .willReturn(List.of());
        given(notificationSettingRepository.findVolunteerScheduleDisabledUserIds(List.of(1L, 2L)))
                .willReturn(List.of());
        given(notificationWriter.createScheduled(eq(1L), any(), any(), any(), any(), any()))
                .willThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.createNotifications(TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1건");
        verify(notificationWriter).createScheduled(eq(2L), any(), any(), any(), eq(20L), any());
    }

    @Test
    void 중복키가_아닌_무결성_오류는_작업_실패로_보고한다() {
        VolunteerScheduleTarget target = target(1L, 10L, "한강공원 플로깅");
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(7)))
                .willReturn(List.of(target));
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(1)))
                .willReturn(List.of());
        given(notificationSettingRepository.findVolunteerScheduleDisabledUserIds(List.of(1L)))
                .willReturn(List.of());
        given(notificationWriter.createScheduled(any(), any(), any(), any(), any(), any()))
                .willThrow(new DataIntegrityViolationException("foreign key violation"));

        assertThatThrownBy(() -> service.createNotifications(TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1건");
    }

    @Test
    void 긴_공고명은_말줄임표를_포함해_170자로_축약한다() {
        String longTitle = "가".repeat(171);
        VolunteerScheduleTarget target = target(1L, 10L, longTitle);
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(7)))
                .willReturn(List.of(target));
        given(postingParticipationRepository.findVolunteerScheduleTargets(TODAY.plusDays(1)))
                .willReturn(List.of());
        given(notificationSettingRepository.findVolunteerScheduleDisabledUserIds(List.of(1L)))
                .willReturn(List.of());

        service.createNotifications(TODAY);

        verify(notificationWriter)
                .createScheduled(
                        eq(1L),
                        eq(NotificationType.VOLUNTEER_SCHEDULE),
                        argThat(message -> message.startsWith("[" + "가".repeat(169) + "…]")),
                        eq(NotificationTargetType.POSTING),
                        eq(10L),
                        any());
    }

    private VolunteerScheduleTarget target(Long userId, Long postingId, String title) {
        return new VolunteerScheduleTarget(userId, postingId, title);
    }
}
