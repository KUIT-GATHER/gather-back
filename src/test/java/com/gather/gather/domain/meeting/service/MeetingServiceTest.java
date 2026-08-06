package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.event.BadgeAwardRequestedEvent;
import com.gather.gather.domain.badge.event.MeetingCompletedEvent;
import com.gather.gather.domain.meeting.dto.MeetingDetailResponse;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.dto.MeetingUpdateRequest;
import com.gather.gather.domain.meeting.dto.PostingMeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.posting.service.RegionNameResolver;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    @Mock private MeetingBookmarkRepository meetingBookmarkRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private MeetingSearchLogService meetingSearchLogService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private RegionNameResolver regionNameResolver;

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

    @Test
    @DisplayName("getMeetings resolves regionName via RegionNameResolver for each meeting")
    void getMeetings_populatesRegionNameFromResolver() {
        when(meeting.getRegionId()).thenReturn(5L);
        when(meetingRepository.searchMeetings(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        anyList(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(meeting), PageRequest.of(0, 10), 1));
        when(regionNameResolver.resolve(List.of(5L))).thenReturn(Map.of(5L, "동구"));

        PageResponse<MeetingResponse> responses =
                meetingService.getMeetings(
                        null, null, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(responses.content().get(0).regionName()).isEqualTo("동구");
    }

    @Test
    @DisplayName("getMyMeetings resolves regionName via RegionNameResolver for each membership")
    void getMyMeetings_populatesRegionNameFromResolver() {
        setAuthenticatedUser(1L);
        when(meeting.getRegionId()).thenReturn(7L);
        MeetingMember membership = createMembership(MeetingMemberRole.MEMBER);
        when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        1L, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of(membership));
        when(regionNameResolver.resolve(List.of(7L))).thenReturn(Map.of(7L, "서구"));

        List<MeetingResponse> responses = meetingService.getMyMeetings();

        assertThat(responses.get(0).regionName()).isEqualTo("서구");
    }

    @Test
    @DisplayName("모임장은 자유 모임의 이름·정원·카테고리·지역을 수정할 수 있다")
    void updateMeeting_updatesFreeMeeting_whenCalledByHost() {
        setAuthenticatedUser(1L);
        Meeting freeMeeting = freeMeeting(20);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(Optional.of(freeMeeting));
        when(regionRepository.existsById(99L)).thenReturn(true);

        MeetingUpdateRequest request =
                updateRequest(
                        60, Set.of(PostingCategory.WELFARE), 99L, LocalDateTime.now().plusDays(3));

        MeetingDetailResponse response = meetingService.updateMeeting(12L, request);

        assertThat(response.name()).isEqualTo("한강공원 플로깅팀(수정)");
        assertThat(response.maxMember()).isEqualTo(60);
        assertThat(response.regionId()).isEqualTo(99L);
        assertThat(response.categories()).containsExactly(PostingCategory.WELFARE);
        assertThat(response.participationCondition()).isEqualTo("우천 시 취소");
    }

    @Test
    @DisplayName("모임장이 아니면 모임 정보를 수정할 수 없다")
    void updateMeeting_rejectsNonHost() {
        setAuthenticatedUser(2L);
        Meeting freeMeeting = freeMeeting(20);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(Optional.of(freeMeeting));

        MeetingUpdateRequest request =
                updateRequest(
                        60, Set.of(PostingCategory.WELFARE), 99L, LocalDateTime.now().plusDays(3));

        assertThatThrownBy(() -> meetingService.updateMeeting(12L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_HOST_ONLY);
    }

    @Test
    @DisplayName("현재 참여 인원보다 정원을 적게 줄이면 거부한다")
    void updateMeeting_rejectsMaxMemberBelowCurrentMembers() {
        setAuthenticatedUser(1L);
        Meeting freeMeeting = freeMeeting(20);
        freeMeeting.increaseMemberCount();
        freeMeeting.increaseMemberCount();
        freeMeeting.increaseMemberCount(); // currentMemberCount = 4
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(Optional.of(freeMeeting));

        MeetingUpdateRequest request =
                updateRequest(
                        3, Set.of(PostingCategory.WELFARE), 99L, LocalDateTime.now().plusDays(3));

        assertThatThrownBy(() -> meetingService.updateMeeting(12L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode", ErrorCode.MEETING_MAX_BELOW_CURRENT_MEMBER);
    }

    @Test
    @DisplayName("자유 모임은 최대 인원을 100명보다 크게 설정할 수 없다")
    void updateMeeting_rejectsFreeMeetingMaxMemberOverLimit() {
        setAuthenticatedUser(1L);
        Meeting freeMeeting = freeMeeting(20);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(Optional.of(freeMeeting));

        MeetingUpdateRequest request =
                updateRequest(
                        101, Set.of(PostingCategory.WELFARE), 99L, LocalDateTime.now().plusDays(3));

        assertThatThrownBy(() -> meetingService.updateMeeting(12L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_MAX_MEMBER_EXCEEDED);
    }

    @Test
    @DisplayName("공고 기반 모임은 최대 인원을 30명보다 크게 설정할 수 없다")
    void updateMeeting_rejectsPostingMeetingMaxMemberOverLimit() {
        setAuthenticatedUser(1L);
        Meeting postingMeeting = postingMeeting(20);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(Optional.of(postingMeeting));

        MeetingUpdateRequest request = updateRequest(31, null, null, postingMeeting.getDeadline());

        assertThatThrownBy(() -> meetingService.updateMeeting(12L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_MAX_MEMBER_EXCEEDED);
    }

    @Test
    @DisplayName("공고 기반 모임은 요청에 지역·카테고리를 담아도 기존 값을 유지한다")
    void updateMeeting_keepsRegionAndCategoriesForPostingBasedMeeting() {
        setAuthenticatedUser(1L);
        Meeting postingMeeting = postingMeeting(20);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(Optional.of(postingMeeting));

        MeetingUpdateRequest request =
                updateRequest(
                        25, Set.of(PostingCategory.WELFARE), 999L, postingMeeting.getDeadline());

        MeetingDetailResponse response = meetingService.updateMeeting(12L, request);

        assertThat(response.regionId()).isEqualTo(postingMeeting.getRegionId());
        assertThat(response.categories()).isEqualTo(postingMeeting.getCategories());
        assertThat(response.maxMember()).isEqualTo(25);
        verify(regionRepository, never()).existsById(999L);
    }

    @Test
    @DisplayName("자유 모임은 지역 없이 수정할 수 없다")
    void updateMeeting_rejectsFreeMeetingWithoutRegionId() {
        setAuthenticatedUser(1L);
        Meeting freeMeeting = freeMeeting(20);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(Optional.of(freeMeeting));

        MeetingUpdateRequest request =
                updateRequest(
                        60, Set.of(PostingCategory.WELFARE), null, LocalDateTime.now().plusDays(3));

        assertThatThrownBy(() -> meetingService.updateMeeting(12L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("공고 기반 모임은 신청 마감일을 활동 시작 시간 이후로 변경할 수 없다")
    void updateMeeting_rejectsDeadlineAfterActivityStart() {
        setAuthenticatedUser(1L);
        Meeting postingMeeting = postingMeeting(20);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(12L))
                .thenReturn(Optional.of(postingMeeting));

        MeetingUpdateRequest request =
                updateRequest(25, null, null, postingMeeting.getActivityStartAt().plusMinutes(1));

        assertThatThrownBy(() -> meetingService.updateMeeting(12L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_MEETING_TIME);
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

    private Meeting freeMeeting(int maxMember) {
        User host = mock(User.class);
        when(host.getId()).thenReturn(1L);
        return Meeting.create(
                "한강공원 플로깅팀",
                "소개",
                maxMember,
                LocalDateTime.now().plusDays(5),
                null,
                Set.of(PostingCategory.ENVIRONMENT),
                5L,
                host,
                null,
                null,
                null,
                null);
    }

    private Meeting postingMeeting(int maxMember) {
        User host = mock(User.class);
        when(host.getId()).thenReturn(1L);
        LocalDateTime activityStart = LocalDateTime.now().plusDays(5);
        LocalDateTime activityEnd = activityStart.plusHours(3);
        return Meeting.create(
                "한강공원 플로깅팀",
                "소개",
                maxMember,
                activityStart.minusDays(1),
                null,
                Set.of(PostingCategory.ENVIRONMENT),
                5L,
                host,
                null,
                10L,
                activityStart,
                activityEnd);
    }

    private MeetingUpdateRequest updateRequest(
            int maxMember, Set<PostingCategory> categories, Long regionId, LocalDateTime deadline) {
        return new MeetingUpdateRequest(
                "한강공원 플로깅팀(수정)", "소개 수정", maxMember, deadline, categories, "우천 시 취소", regionId);
    }
}
