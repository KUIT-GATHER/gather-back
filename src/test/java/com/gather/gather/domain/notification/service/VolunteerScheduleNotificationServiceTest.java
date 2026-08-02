package com.gather.gather.domain.notification.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.model.VolunteerScheduleTarget;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VolunteerScheduleNotificationServiceTest {

    @Mock private PostingParticipationRepository postingParticipationRepository;

    @Mock private NotificationSettingRepository notificationSettingRepository;

    @Mock private NotificationWriter notificationWriter;

    @InjectMocks private VolunteerScheduleNotificationService service;

    @Test
    @DisplayName("봉사 시작 7일 전 일정 알림을 생성한다")
    void createNotificationsCreatesWeekBeforeNotification() {
        LocalDate today = LocalDate.of(2026, 8, 2);

        VolunteerScheduleTarget target = new VolunteerScheduleTarget(1L, 10L, "한강공원 플로깅");

        given(postingParticipationRepository.findVolunteerScheduleTargets(today.plusDays(7)))
                .willReturn(List.of(target));

        given(postingParticipationRepository.findVolunteerScheduleTargets(today.plusDays(1)))
                .willReturn(List.of());

        given(notificationSettingRepository.findVolunteerScheduleDisabledUserIds(List.of(1L)))
                .willReturn(List.of());

        int count = service.createNotifications(today);

        assertThat(count).isEqualTo(1);

        verify(notificationWriter)
                .createScheduled(
                        eq(1L),
                        eq(NotificationType.VOLUNTEER_SCHEDULE),
                        eq("[한강공원 플로깅] 봉사 일정이 일주일 남았어요. 시간과 장소를 확인해 주세요."),
                        eq(NotificationTargetType.POSTING),
                        eq(10L),
                        contains("VOLUNTEER_SCHEDULE:1:10:2026-08-09:7"));
    }
}
