package com.gather.gather.domain.badge.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
    private static final Set<PostingParticipationStatus> COMPLETED_STATUSES =
            Set.of(PostingParticipationStatus.COMPLETED, PostingParticipationStatus.REVIEWED);

    @Mock private PostingParticipationRepository postingParticipationRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private BadgeAwardService badgeAwardService;

    private BadgeEvaluationService badgeEvaluationService;
    private long nextPostingId;

    @BeforeEach
    void setUp() {
        badgeEvaluationService =
                new BadgeEvaluationService(
                        postingParticipationRepository,
                        postingRepository,
                        meetingMemberRepository,
                        badgeAwardService);
        nextPostingId = 100L;
        lenient()
                .when(
                        meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                                USER_ID, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED))
                .thenReturn(List.of());
        lenient()
                .when(
                        postingParticipationRepository.findAllByUserIdAndStatusIn(
                                USER_ID, COMPLETED_STATUSES))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("awards FIRST_COMPLETION when a single completion exists")
    void onVolunteerActivityCompleted_awardsFirstCompletion_whenOneCompletionExists() {
        givenCompletedPostingActivityDates(LocalDate.of(2026, 1, 10));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.FIRST_COMPLETION);
        verify(badgeAwardService, never()).award(USER_ID, BadgeType.COMPLETION_5);
        verify(badgeAwardService, never()).award(USER_ID, BadgeType.CONSECUTIVE_3_MONTHS);
    }

    @Test
    @DisplayName("does not award COMPLETION_5 when exactly four completions exist (M-11 boundary)")
    void onVolunteerActivityCompleted_doesNotAwardCompletion5_whenFourCompletionsExist() {
        givenCompletedPostingActivityDates(
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 13));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService, never()).award(USER_ID, BadgeType.COMPLETION_5);
    }

    @Test
    @DisplayName("awards COMPLETION_5 once five completions exist")
    void onVolunteerActivityCompleted_awardsCompletion5_whenFiveCompletionsExist() {
        givenCompletedPostingActivityDates(
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 1, 12),
                LocalDate.of(2026, 1, 13),
                LocalDate.of(2026, 1, 14));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.COMPLETION_5);
    }

    @Test
    @DisplayName(
            "does not award CONSECUTIVE_3_MONTHS when only two consecutive months exist (M-11"
                    + " boundary)")
    void onVolunteerActivityCompleted_doesNotAwardConsecutiveMonths_whenOnlyTwoMonthsInARow() {
        givenCompletedPostingActivityDates(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 5));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService, never()).award(USER_ID, BadgeType.CONSECUTIVE_3_MONTHS);
    }

    @Test
    @DisplayName(
            "awards CONSECUTIVE_3_MONTHS when three consecutive calendar months have a"
                    + " completion")
    void onVolunteerActivityCompleted_awardsConsecutiveMonths_whenThreeMonthsInARow() {
        givenCompletedPostingActivityDates(
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 5), LocalDate.of(2026, 3, 20));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.CONSECUTIVE_3_MONTHS);
    }

    @Test
    @DisplayName("does not award CONSECUTIVE_3_MONTHS when there is a gap month")
    void onVolunteerActivityCompleted_skipsConsecutiveMonths_whenGapExists() {
        givenCompletedPostingActivityDates(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 3, 20));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService, never()).award(USER_ID, BadgeType.CONSECUTIVE_3_MONTHS);
    }

    @Test
    @DisplayName("does nothing when there are no completions")
    void onVolunteerActivityCompleted_doesNothing_whenNoCompletions() {
        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService, never()).award(USER_ID, BadgeType.FIRST_COMPLETION);
    }

    @Test
    @DisplayName(
            "uses the posting's actual activity date (not completedAt) so completing several"
                    + " months of activity in one sitting still credits each activity month (M-2)")
    void onVolunteerActivityCompleted_usesActivityDate_notCompletedAt() {
        List<PostingParticipation> participations =
                givenCompletedPostingActivityDates(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 2, 5),
                        LocalDate.of(2026, 3, 20));
        // 세 건 모두 4월에 한꺼번에 완료 처리했다고 가정한다 — completedAt 기준이라면 연속봉사가 성립하지 않아야 한다.
        LocalDateTime completedInApril = LocalDate.of(2026, 4, 1).atStartOfDay();
        participations.forEach(
                participation ->
                        ReflectionTestUtils.setField(
                                participation, "completedAt", completedInApril));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.CONSECUTIVE_3_MONTHS);
    }

    @Test
    @DisplayName("includes REVIEWED participations, not only COMPLETED (M-5)")
    void onVolunteerActivityCompleted_includesReviewedParticipations() {
        Posting posting = postingEndingOn(nextPostingId++, LocalDate.of(2026, 1, 10));
        PostingParticipation reviewed = participationFor(posting.getId());
        ReflectionTestUtils.setField(reviewed, "status", PostingParticipationStatus.REVIEWED);
        when(postingParticipationRepository.findAllByUserIdAndStatusIn(USER_ID, COMPLETED_STATUSES))
                .thenReturn(List.of(reviewed));
        when(postingRepository.findAllById(any())).thenReturn(List.of(posting));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.FIRST_COMPLETION);
    }

    @Test
    @DisplayName(
            "aggregates completed meeting volunteering together with posting participation"
                    + " (M-12)")
    void onVolunteerActivityCompleted_aggregatesMeetingCompletions() {
        givenCompletedPostingActivityDates(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 5));
        MeetingMember completedMember =
                approvedMemberOf(meetingEndingOn(LocalDate.of(2026, 3, 20)));
        when(meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                        USER_ID, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED))
                .thenReturn(List.of(completedMember));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.CONSECUTIVE_3_MONTHS);
    }

    @Test
    @DisplayName("falls back to completedAt for a free meeting with no activity period set")
    void onVolunteerActivityCompleted_fallsBackToCompletedAt_whenMeetingHasNoActivityPeriod() {
        Meeting meeting = freeMeetingCompletedOn(LocalDate.of(2026, 5, 1));
        MeetingMember freeMember = approvedMemberOf(meeting);
        when(meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                        USER_ID, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED))
                .thenReturn(List.of(freeMember));

        badgeEvaluationService.onVolunteerActivityCompleted(USER_ID);

        verify(badgeAwardService).award(USER_ID, BadgeType.FIRST_COMPLETION);
    }

    /** 각 날짜마다 별도 postingId를 부여해 (participation, posting) 쌍을 만들고 리포지터리 스텁을 채운다. */
    private List<PostingParticipation> givenCompletedPostingActivityDates(
            LocalDate... activityDates) {
        List<Posting> postings =
                java.util.Arrays.stream(activityDates)
                        .map(date -> postingEndingOn(nextPostingId++, date))
                        .toList();
        List<PostingParticipation> participations =
                postings.stream().map(posting -> participationFor(posting.getId())).toList();

        when(postingParticipationRepository.findAllByUserIdAndStatusIn(USER_ID, COMPLETED_STATUSES))
                .thenReturn(participations);
        when(postingRepository.findAllById(any())).thenReturn(postings);
        return participations;
    }

    private PostingParticipation participationFor(Long postingId) {
        PostingParticipation participation = PostingParticipation.create(USER_ID, postingId);
        ReflectionTestUtils.setField(participation, "status", PostingParticipationStatus.COMPLETED);
        ReflectionTestUtils.setField(participation, "completedAt", LocalDateTime.now());
        return participation;
    }

    private Posting postingEndingOn(Long postingId, LocalDate activityDate) {
        Posting posting =
                Posting.builder()
                        .title("테스트 공고")
                        .status(PostingStatus.RECRUITING)
                        .activityDate(activityDate)
                        .category(PostingCategory.ENVIRONMENT)
                        .isActive(true)
                        .build();
        ReflectionTestUtils.setField(posting, "id", postingId);
        return posting;
    }

    private Meeting meetingEndingOn(LocalDate activityEndDate) {
        Meeting meeting =
                Meeting.create(
                        "테스트 모임",
                        "설명",
                        5,
                        activityEndDate.minusDays(10).atStartOfDay(),
                        null,
                        Set.of(PostingCategory.ENVIRONMENT),
                        1L,
                        mockUser(),
                        null,
                        null,
                        activityEndDate.minusDays(1).atStartOfDay(),
                        activityEndDate.atStartOfDay());
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);
        return meeting;
    }

    private Meeting freeMeetingCompletedOn(LocalDate completedDate) {
        Meeting meeting =
                Meeting.create(
                        "자유 모임",
                        "설명",
                        5,
                        completedDate.minusDays(10).atStartOfDay(),
                        null,
                        Set.of(PostingCategory.ENVIRONMENT),
                        1L,
                        mockUser(),
                        null,
                        null,
                        null,
                        null);
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);
        ReflectionTestUtils.setField(meeting, "completedAt", completedDate.atStartOfDay());
        return meeting;
    }

    private MeetingMember approvedMemberOf(Meeting meeting) {
        MeetingMember member = MeetingMember.createHost(mockUser(), meeting);
        ReflectionTestUtils.setField(member, "status", MeetingMemberStatus.APPROVED);
        return member;
    }

    private User mockUser() {
        User user = org.mockito.Mockito.mock(User.class);
        lenient().when(user.getId()).thenReturn(USER_ID);
        return user;
    }
}
