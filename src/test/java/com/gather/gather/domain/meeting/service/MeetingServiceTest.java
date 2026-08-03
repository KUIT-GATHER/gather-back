package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.event.BadgeAwardRequestedEvent;
import com.gather.gather.domain.badge.event.MeetingCompletedEvent;
import com.gather.gather.domain.meeting.dto.PostingMeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private MeetingSearchLogService meetingSearchLogService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks private MeetingService meetingService;

    private Meeting meeting;

    @BeforeEach
    void setUp() {
        meeting = mock(Meeting.class);
        org.mockito.Mockito.lenient().when(meeting.getId()).thenReturn(12L);
        org.mockito.Mockito.lenient().when(meeting.getName()).thenReturn("한강공원 플로깅팀");
        org.mockito.Mockito.lenient()
                .when(meeting.getCategories())
                .thenReturn(Set.of(PostingCategory.ENVIRONMENT));
        org.mockito.Mockito.lenient().when(meeting.getCurrentMemberCount()).thenReturn(12);
        org.mockito.Mockito.lenient().when(meeting.getMaxMember()).thenReturn(20);
        org.mockito.Mockito.lenient()
                .when(meeting.getStatus())
                .thenReturn(MeetingStatus.RECRUITING);
        org.mockito.Mockito.lenient()
                .when(meeting.isActivityEnded(org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        org.mockito.Mockito.lenient()
                .when(meeting.isDeadlinePassed(org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        org.mockito.Mockito.lenient().when(meeting.isFull()).thenReturn(false);

        org.mockito.Mockito.lenient().when(postingRepository.existsById(10L)).thenReturn(true);
        org.mockito.Mockito.lenient()
                .when(
                        meetingRepository.findAllByVolunteerPostingIdAndDeletedAtIsNull(
                                eq(10L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(meeting), PageRequest.of(0, 10), 1));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("비로그인 사용자는 member와 host가 모두 false다")
    void getMeetingsByPosting_returnsFalseFlagsForAnonymousUser() {
        PostingMeetingResponse response =
                meetingService.getMeetingsByPosting(10L, PageRequest.of(0, 10)).content().get(0);

        assertThat(response.member()).isFalse();
        assertThat(response.host()).isFalse();
        verify(meetingMemberRepository, never())
                .findAllByUserIdAndStatusAndMeetingIdInFetchMeeting(
                        eq(1L), eq(MeetingMemberStatus.APPROVED), anyList());
    }

    @Test
    @DisplayName("일반 가입자는 member만 true다")
    void getMeetingsByPosting_returnsMemberFlagForMember() {
        setAuthenticatedUser(1L);
        MeetingMember membership = createMembership(MeetingMemberRole.MEMBER);
        when(meetingMemberRepository.findAllByUserIdAndStatusAndMeetingIdInFetchMeeting(
                        eq(1L), eq(MeetingMemberStatus.APPROVED), anyList()))
                .thenReturn(List.of(membership));

        PostingMeetingResponse response =
                meetingService.getMeetingsByPosting(10L, PageRequest.of(0, 10)).content().get(0);

        assertThat(response.member()).isTrue();
        assertThat(response.host()).isFalse();
    }

    @Test
    @DisplayName("모임장은 member와 host가 모두 true다")
    void getMeetingsByPosting_returnsMemberAndHostFlagsForHost() {
        setAuthenticatedUser(1L);
        MeetingMember membership = createMembership(MeetingMemberRole.HOST);
        when(meetingMemberRepository.findAllByUserIdAndStatusAndMeetingIdInFetchMeeting(
                        eq(1L), eq(MeetingMemberStatus.APPROVED), anyList()))
                .thenReturn(List.of(membership));

        PageResponse<PostingMeetingResponse> responses =
                meetingService.getMeetingsByPosting(10L, PageRequest.of(0, 10));
        PostingMeetingResponse response = responses.content().get(0);

        assertThat(response.member()).isTrue();
        assertThat(response.host()).isTrue();
    }

    @Test
    @DisplayName("completeMeeting completes the meeting when called by the host")
    void completeMeeting_completesMeeting_whenCalledByHost() {
        setAuthenticatedUser(1L);
        Meeting hostMeeting = mock(Meeting.class);
        com.gather.gather.domain.auth.entity.User host =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(host.getId()).thenReturn(1L);
        when(hostMeeting.getHost()).thenReturn(host);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(java.util.Optional.of(hostMeeting));

        meetingService.completeMeeting(12L);

        verify(hostMeeting).complete();
        verify(eventPublisher).publishEvent(new MeetingCompletedEvent(12L));
    }

    @Test
    @DisplayName(
            "completeMeeting publishes MeetingCompletedEvent after commit instead of evaluating"
                    + " member badges inline (B-1 — per-member isolation now lives in"
                    + " BadgeEventListener, covered by BadgeEventListenerTest)")
    void completeMeeting_publishesEventInsteadOfEvaluatingBadgesInline() {
        setAuthenticatedUser(1L);
        Meeting hostMeeting = mock(Meeting.class);
        com.gather.gather.domain.auth.entity.User host =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(host.getId()).thenReturn(1L);
        when(hostMeeting.getHost()).thenReturn(host);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(java.util.Optional.of(hostMeeting));

        meetingService.completeMeeting(12L);

        verify(hostMeeting).complete();
        verify(eventPublisher).publishEvent(new MeetingCompletedEvent(12L));
        verify(meetingMemberRepository, never())
                .findAllByMeetingIdAndStatusFetchUser(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName(
            "completeMeeting throws MEETING_COMPLETE_NOT_ALLOWED when the meeting has an activity"
                    + " period that has not ended yet (M-1)")
    void completeMeeting_throwsCompleteNotAllowed_whenActivityNotEnded() {
        setAuthenticatedUser(1L);
        Meeting hostMeeting = mock(Meeting.class);
        com.gather.gather.domain.auth.entity.User host =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(host.getId()).thenReturn(1L);
        when(hostMeeting.getHost()).thenReturn(host);
        when(hostMeeting.hasActivityPeriod()).thenReturn(true);
        when(hostMeeting.isActivityEnded(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(java.util.Optional.of(hostMeeting));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> meetingService.completeMeeting(12L))
                .isInstanceOf(com.gather.gather.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        com.gather.gather.global.exception.ErrorCode.MEETING_COMPLETE_NOT_ALLOWED);
        verify(hostMeeting, never()).complete();
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName(
            "completeMeeting succeeds when the meeting has an activity period that has already"
                    + " ended (M-1)")
    void completeMeeting_succeeds_whenActivityHasEnded() {
        setAuthenticatedUser(1L);
        Meeting hostMeeting = mock(Meeting.class);
        com.gather.gather.domain.auth.entity.User host =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(host.getId()).thenReturn(1L);
        when(hostMeeting.getHost()).thenReturn(host);
        when(hostMeeting.hasActivityPeriod()).thenReturn(true);
        when(hostMeeting.isActivityEnded(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(java.util.Optional.of(hostMeeting));

        meetingService.completeMeeting(12L);

        verify(hostMeeting).complete();
        verify(eventPublisher).publishEvent(new MeetingCompletedEvent(12L));
    }

    @Test
    @DisplayName("completeMeeting throws MEETING_HOST_ONLY when called by a non-host")
    void completeMeeting_throwsHostOnly_whenNotHost() {
        setAuthenticatedUser(2L);
        Meeting hostMeeting = mock(Meeting.class);
        com.gather.gather.domain.auth.entity.User host =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(host.getId()).thenReturn(1L);
        when(hostMeeting.getHost()).thenReturn(host);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(java.util.Optional.of(hostMeeting));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> meetingService.completeMeeting(12L))
                .isInstanceOf(com.gather.gather.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        com.gather.gather.global.exception.ErrorCode.MEETING_HOST_ONLY);
        verify(hostMeeting, never()).complete();
    }

    @Test
    @DisplayName("completeMeeting throws MEETING_ALREADY_COMPLETED when already completed")
    void completeMeeting_throwsAlreadyCompleted_whenAlreadyCompleted() {
        setAuthenticatedUser(1L);
        Meeting hostMeeting = mock(Meeting.class);
        com.gather.gather.domain.auth.entity.User host =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(host.getId()).thenReturn(1L);
        when(hostMeeting.getHost()).thenReturn(host);
        when(hostMeeting.getStatus()).thenReturn(MeetingStatus.COMPLETED);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(java.util.Optional.of(hostMeeting));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> meetingService.completeMeeting(12L))
                .isInstanceOf(com.gather.gather.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        com.gather.gather.global.exception.ErrorCode.MEETING_ALREADY_COMPLETED);
        verify(hostMeeting, never()).complete();
    }

    @Test
    @DisplayName(
            "submitMemberHours stores the minutes for an approved member of a completed meeting")
    void submitMemberHours_storesMinutes_whenMeetingCompleted() {
        setAuthenticatedUser(1L);
        Meeting completedMeeting = mock(Meeting.class);
        when(completedMeeting.getStatus()).thenReturn(MeetingStatus.COMPLETED);
        when(meetingRepository.findByIdAndDeletedAtIsNull(12L))
                .thenReturn(java.util.Optional.of(completedMeeting));
        MeetingMember member = mock(MeetingMember.class);
        when(member.getRecognizedMinutes()).thenReturn(null);
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        12L, 1L, MeetingMemberStatus.APPROVED))
                .thenReturn(java.util.Optional.of(member));

        meetingService.submitMemberHours(12L, 210);

        verify(member).submitRecognizedMinutes(210);
    }

    @Test
    @DisplayName(
            "submitMemberHours throws MEETING_HOURS_NOT_ALLOWED when the meeting is not"
                    + " completed yet")
    void submitMemberHours_throwsHoursNotAllowed_whenNotCompleted() {
        setAuthenticatedUser(1L);
        Meeting recruitingMeeting = mock(Meeting.class);
        when(recruitingMeeting.getStatus()).thenReturn(MeetingStatus.RECRUITING);
        when(meetingRepository.findByIdAndDeletedAtIsNull(12L))
                .thenReturn(java.util.Optional.of(recruitingMeeting));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> meetingService.submitMemberHours(12L, 210))
                .isInstanceOf(com.gather.gather.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        com.gather.gather.global.exception.ErrorCode.MEETING_HOURS_NOT_ALLOWED);
    }

    @Test
    @DisplayName(
            "submitMemberHours throws VALIDATION_ERROR when minutes is not a positive"
                    + " multiple of 10")
    void submitMemberHours_throwsValidationError_whenNotMultipleOfTen() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> meetingService.submitMemberHours(12L, 15))
                .isInstanceOf(com.gather.gather.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", com.gather.gather.global.exception.ErrorCode.VALIDATION_ERROR);
        verify(meetingRepository, never()).findByIdAndDeletedAtIsNull(12L);
    }

    @Test
    @DisplayName(
            "submitMemberHours throws MEETING_MEMBER_REQUIRED when the caller is not an"
                    + " approved member (M-10)")
    void submitMemberHours_throwsMemberRequired_whenNotApprovedMember() {
        setAuthenticatedUser(1L);
        Meeting completedMeeting = mock(Meeting.class);
        when(completedMeeting.getStatus()).thenReturn(MeetingStatus.COMPLETED);
        when(meetingRepository.findByIdAndDeletedAtIsNull(12L))
                .thenReturn(java.util.Optional.of(completedMeeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        12L, 1L, MeetingMemberStatus.APPROVED))
                .thenReturn(java.util.Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> meetingService.submitMemberHours(12L, 210))
                .isInstanceOf(com.gather.gather.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        com.gather.gather.global.exception.ErrorCode.MEETING_MEMBER_REQUIRED);
    }

    @Test
    @DisplayName(
            "submitMemberHours throws MEETING_HOURS_ALREADY_SUBMITTED when minutes were already"
                    + " entered (M-10)")
    void submitMemberHours_throwsAlreadySubmitted_whenAlreadySet() {
        setAuthenticatedUser(1L);
        Meeting completedMeeting = mock(Meeting.class);
        when(completedMeeting.getStatus()).thenReturn(MeetingStatus.COMPLETED);
        when(meetingRepository.findByIdAndDeletedAtIsNull(12L))
                .thenReturn(java.util.Optional.of(completedMeeting));
        MeetingMember member = mock(MeetingMember.class);
        when(member.getRecognizedMinutes()).thenReturn(60);
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        12L, 1L, MeetingMemberStatus.APPROVED))
                .thenReturn(java.util.Optional.of(member));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> meetingService.submitMemberHours(12L, 210))
                .isInstanceOf(com.gather.gather.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        com.gather.gather.global.exception.ErrorCode
                                .MEETING_HOURS_ALREADY_SUBMITTED);
        verify(member, never()).submitRecognizedMinutes(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName(
            "approveJoinRequest publishes FIRST_TEAM_JOIN when the approved requester's role is"
                    + " MEMBER (H-3)")
    void approveJoinRequest_publishesFirstTeamJoin_whenRoleIsMember() {
        setAuthenticatedUser(1L);
        Meeting hostMeeting = mock(Meeting.class);
        com.gather.gather.domain.auth.entity.User host =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(host.getId()).thenReturn(1L);
        when(hostMeeting.getHost()).thenReturn(host);
        when(hostMeeting.getStatus()).thenReturn(MeetingStatus.RECRUITING);
        when(hostMeeting.isDeadlinePassed(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(hostMeeting.isActivityEnded(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(hostMeeting.isFull()).thenReturn(false);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(java.util.Optional.of(hostMeeting));

        MeetingMember pendingMember = mock(MeetingMember.class);
        com.gather.gather.domain.auth.entity.User requester =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(requester.getId()).thenReturn(9L);
        when(pendingMember.getUser()).thenReturn(requester);
        when(pendingMember.getRole()).thenReturn(MeetingMemberRole.MEMBER);
        when(meetingMemberRepository.findPendingByIdAndMeetingIdForUpdate(99L, 12L))
                .thenReturn(java.util.Optional.of(pendingMember));

        meetingService.approveJoinRequest(12L, 99L);

        verify(pendingMember).approve();
        verify(eventPublisher)
                .publishEvent(new BadgeAwardRequestedEvent(9L, BadgeType.FIRST_TEAM_JOIN));
    }

    @Test
    @DisplayName(
            "approveJoinRequest does not publish FIRST_TEAM_JOIN when the approved requester's"
                    + " role is HOST (H-3)")
    void approveJoinRequest_doesNotPublishFirstTeamJoin_whenRoleIsHost() {
        setAuthenticatedUser(1L);
        Meeting hostMeeting = mock(Meeting.class);
        com.gather.gather.domain.auth.entity.User host =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(host.getId()).thenReturn(1L);
        when(hostMeeting.getHost()).thenReturn(host);
        when(hostMeeting.getStatus()).thenReturn(MeetingStatus.RECRUITING);
        when(hostMeeting.isDeadlinePassed(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(hostMeeting.isActivityEnded(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(hostMeeting.isFull()).thenReturn(false);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(java.util.Optional.of(hostMeeting));

        MeetingMember pendingMember = mock(MeetingMember.class);
        com.gather.gather.domain.auth.entity.User requester =
                mock(com.gather.gather.domain.auth.entity.User.class);
        when(pendingMember.getUser()).thenReturn(requester);
        when(pendingMember.getRole()).thenReturn(MeetingMemberRole.HOST);
        when(meetingMemberRepository.findPendingByIdAndMeetingIdForUpdate(99L, 12L))
                .thenReturn(java.util.Optional.of(pendingMember));

        meetingService.approveJoinRequest(12L, 99L);

        verify(pendingMember).approve();
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private MeetingMember createMembership(MeetingMemberRole role) {
        MeetingMember membership = mock(MeetingMember.class);
        when(membership.getMeeting()).thenReturn(meeting);
        when(membership.getRole()).thenReturn(role);
        return membership;
    }
}
