package com.gather.gather.domain.badge.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.event.BadgeAwardRequestedEvent;
import com.gather.gather.domain.badge.event.MeetingCompletedEvent;
import com.gather.gather.domain.badge.event.VolunteerActivityCompletedEvent;
import com.gather.gather.domain.badge.service.BadgeAwardService;
import com.gather.gather.domain.badge.service.BadgeEvaluationService;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BadgeEventListener는 AFTER_COMMIT 시점에 호출되므로, 여기서 발생하는 예외는 이미 커밋된 본 처리에 영향을 줄 수 없다(B-1). 이 테스트는 그
 * 격리가 실제로 동작하는지 — 멤버 한 명의 평가 실패가 나머지 멤버 평가를 막지 않는지 — 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BadgeEventListenerTest {

    @Mock private BadgeAwardService badgeAwardService;
    @Mock private BadgeEvaluationService badgeEvaluationService;
    @Mock private MeetingMemberRepository meetingMemberRepository;

    private BadgeEventListener badgeEventListener;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        badgeEventListener =
                new BadgeEventListener(
                        badgeAwardService, badgeEvaluationService, meetingMemberRepository);
    }

    @Test
    @DisplayName("onBadgeAwardRequested delegates to BadgeAwardService.award")
    void onBadgeAwardRequested_delegatesToAward() {
        badgeEventListener.onBadgeAwardRequested(
                new BadgeAwardRequestedEvent(1L, BadgeType.BOOKMARK_5));

        verify(badgeAwardService).award(1L, BadgeType.BOOKMARK_5);
    }

    @Test
    @DisplayName(
            "onBadgeAwardRequested swallows a RuntimeException so the listener does not propagate it")
    void onBadgeAwardRequested_swallowsException() {
        doThrow(new RuntimeException("award failed"))
                .when(badgeAwardService)
                .award(1L, BadgeType.BOOKMARK_5);

        badgeEventListener.onBadgeAwardRequested(
                new BadgeAwardRequestedEvent(1L, BadgeType.BOOKMARK_5));
    }

    @Test
    @DisplayName("onVolunteerActivityCompleted delegates to BadgeEvaluationService")
    void onVolunteerActivityCompleted_delegates() {
        badgeEventListener.onVolunteerActivityCompleted(new VolunteerActivityCompletedEvent(1L));

        verify(badgeEvaluationService).onVolunteerActivityCompleted(1L);
    }

    @Test
    @DisplayName(
            "onMeetingCompleted evaluates every approved member's badges even when one member's"
                    + " evaluation throws (B-1 / L-8 — the isolation the old inline try/catch"
                    + " claimed to provide but did not)")
    void onMeetingCompleted_isolatesPerMemberBadgeFailures() {
        MeetingMember failingMember = mock(MeetingMember.class);
        User failingUser = mock(User.class);
        when(failingUser.getId()).thenReturn(2L);
        when(failingMember.getUser()).thenReturn(failingUser);

        MeetingMember okMember = mock(MeetingMember.class);
        User okUser = mock(User.class);
        when(okUser.getId()).thenReturn(3L);
        when(okMember.getUser()).thenReturn(okUser);

        when(meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                        12L, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of(failingMember, okMember));
        doThrow(new RuntimeException("badge evaluation blew up"))
                .when(badgeEvaluationService)
                .onVolunteerActivityCompleted(2L);

        badgeEventListener.onMeetingCompleted(new MeetingCompletedEvent(12L));

        verify(badgeEvaluationService).onVolunteerActivityCompleted(2L);
        verify(badgeEvaluationService).onVolunteerActivityCompleted(3L);
    }

    @Test
    @DisplayName("onMeetingCompleted does nothing when there are no approved members")
    void onMeetingCompleted_doesNothing_whenNoApprovedMembers() {
        when(meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                        12L, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of());

        badgeEventListener.onMeetingCompleted(new MeetingCompletedEvent(12L));

        verify(badgeEvaluationService, org.mockito.Mockito.never())
                .onVolunteerActivityCompleted(any());
    }
}
