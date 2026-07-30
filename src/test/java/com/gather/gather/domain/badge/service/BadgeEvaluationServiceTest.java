package com.gather.gather.domain.badge.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BadgeEvaluationServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private PostingParticipationRepository postingParticipationRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private BadgeAwardService badgeAwardService;

    private BadgeEvaluationService badgeEvaluationService;

    @BeforeEach
    void setUp() {
        badgeEvaluationService =
                new BadgeEvaluationService(
                        postingParticipationRepository, meetingMemberRepository, badgeAwardService);
        when(meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                        USER_ID, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("awards FIRST_COMPLETION when a single completion exists")
    void onVolunteerActivityCompleted_awardsFirstCompletion_whenOneCompletionExists() {
        when(postingParticipationRepository.findAllByUserIdAndStatus(
                        USER_ID, PostingParticipationStatus.COMPLETED))
                .thenReturn(List.of(completedAt(LocalDate.of(2026, 1, 10))));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.FIRST_COMPLETION);
        verify(badgeAwardService, never()).award(USER_ID, BadgeType.COMPLETION_5);
        verify(badgeAwardService, never()).award(USER_ID, BadgeType.CONSECUTIVE_3_MONTHS);
    }

    @Test
    @DisplayName("awards COMPLETION_5 once five completions exist")
    void onVolunteerActivityCompleted_awardsCompletion5_whenFiveCompletionsExist() {
        when(postingParticipationRepository.findAllByUserIdAndStatus(
                        USER_ID, PostingParticipationStatus.COMPLETED))
                .thenReturn(
                        List.of(
                                completedAt(LocalDate.of(2026, 1, 10)),
                                completedAt(LocalDate.of(2026, 1, 11)),
                                completedAt(LocalDate.of(2026, 1, 12)),
                                completedAt(LocalDate.of(2026, 1, 13)),
                                completedAt(LocalDate.of(2026, 1, 14))));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.COMPLETION_5);
    }

    @Test
    @DisplayName(
            "awards CONSECUTIVE_3_MONTHS when three consecutive calendar months have a"
                    + " completion")
    void onVolunteerActivityCompleted_awardsConsecutiveMonths_whenThreeMonthsInARow() {
        when(postingParticipationRepository.findAllByUserIdAndStatus(
                        USER_ID, PostingParticipationStatus.COMPLETED))
                .thenReturn(
                        List.of(
                                completedAt(LocalDate.of(2026, 1, 10)),
                                completedAt(LocalDate.of(2026, 2, 5)),
                                completedAt(LocalDate.of(2026, 3, 20))));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.CONSECUTIVE_3_MONTHS);
    }

    @Test
    @DisplayName("does not award CONSECUTIVE_3_MONTHS when there is a gap month")
    void onVolunteerActivityCompleted_skipsConsecutiveMonths_whenGapExists() {
        when(postingParticipationRepository.findAllByUserIdAndStatus(
                        USER_ID, PostingParticipationStatus.COMPLETED))
                .thenReturn(
                        List.of(
                                completedAt(LocalDate.of(2026, 1, 10)),
                                completedAt(LocalDate.of(2026, 3, 20))));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService, never()).award(USER_ID, BadgeType.CONSECUTIVE_3_MONTHS);
    }

    @Test
    @DisplayName("does nothing when there are no completions")
    void onVolunteerActivityCompleted_doesNothing_whenNoCompletions() {
        when(postingParticipationRepository.findAllByUserIdAndStatus(
                        USER_ID, PostingParticipationStatus.COMPLETED))
                .thenReturn(List.of());

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService, never()).award(USER_ID, BadgeType.FIRST_COMPLETION);
    }

    private PostingParticipation completedAt(LocalDate date) {
        PostingParticipation participation = PostingParticipation.create(USER_ID, 10L);
        ReflectionTestUtils.setField(participation, "status", PostingParticipationStatus.COMPLETED);
        ReflectionTestUtils.setField(participation, "updatedAt", date.atStartOfDay());
        return participation;
    }
}
