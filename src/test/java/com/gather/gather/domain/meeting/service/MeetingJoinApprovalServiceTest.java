package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.dto.MeetingJoinRequestResponse;
import com.gather.gather.domain.meeting.dto.MeetingJoinResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MeetingJoinApprovalServiceTest {

    private static final Long MEETING_ID = 1L;
    private static final Long USER_ID = 2L;
    private static final Long HOST_ID = 3L;
    private static final Long JOIN_REQUEST_ID = 4L;

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private MeetingSearchLogService meetingSearchLogService;
    @Mock private com.gather.gather.domain.badge.service.BadgeAwardService badgeAwardService;

    @Mock
    private com.gather.gather.domain.badge.service.BadgeEvaluationService badgeEvaluationService;

    @InjectMocks private MeetingService meetingService;

    @Mock private Meeting meeting;
    @Mock private User user;
    @Mock private User host;

    @BeforeEach
    void setUp() {
        setAuthenticatedUser(USER_ID);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("가입 신청은 승인 대기 상태로 저장되고 현재 인원은 증가하지 않는다")
    void joinMeeting_createsPendingRequestWithoutIncreasingMemberCount() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meeting.getId()).thenReturn(MEETING_ID);
        when(meeting.getStatus()).thenReturn(MeetingStatus.RECRUITING);
        when(meeting.isActivityEnded(any())).thenReturn(false);
        when(meeting.isDeadlinePassed(any())).thenReturn(false);
        when(meeting.isFull()).thenReturn(false);
        when(meetingMemberRepository.findByMeeting_IdAndUser_Id(MEETING_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(meetingMemberRepository.saveAndFlush(any(MeetingMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MeetingJoinResponse response = meetingService.joinMeeting(MEETING_ID);

        assertThat(response.status()).isEqualTo(MeetingMemberStatus.PENDING);
        verify(meeting, never()).increaseMemberCount();
    }

    @Test
    @DisplayName("모임장이 가입 신청을 승인하면 상태와 현재 인원이 변경된다")
    void approveJoinRequest_approvesRequestAndIncreasesMemberCount() {
        setAuthenticatedUser(HOST_ID);
        MeetingMember member = pendingMember();
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meeting.getHost()).thenReturn(host);
        when(host.getId()).thenReturn(HOST_ID);
        when(meeting.isFull()).thenReturn(false);
        when(meetingMemberRepository.findPendingByIdAndMeetingIdForUpdate(
                        JOIN_REQUEST_ID, MEETING_ID))
                .thenReturn(Optional.of(member));

        MeetingJoinRequestResponse response =
                meetingService.approveJoinRequest(MEETING_ID, JOIN_REQUEST_ID);

        assertThat(response.status()).isEqualTo(MeetingMemberStatus.APPROVED);
        verify(meeting).increaseMemberCount();
    }

    @Test
    @DisplayName("모임장이 아닌 사용자는 가입 신청을 승인할 수 없다")
    void approveJoinRequest_rejectsNonHost() {
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meeting.getHost()).thenReturn(host);
        when(host.getId()).thenReturn(HOST_ID);

        assertThatThrownBy(() -> meetingService.approveJoinRequest(MEETING_ID, JOIN_REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEETING_HOST_ONLY);

        verify(meetingMemberRepository, never())
                .findPendingByIdAndMeetingIdForUpdate(JOIN_REQUEST_ID, MEETING_ID);
    }

    @Test
    @DisplayName("모집이 종료된 모임의 가입 신청은 승인할 수 없다")
    void approveJoinRequest_rejectsClosedMeeting() {
        setAuthenticatedUser(HOST_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meeting.getHost()).thenReturn(host);
        when(host.getId()).thenReturn(HOST_ID);
        when(meeting.getStatus()).thenReturn(MeetingStatus.CLOSED);

        assertThatThrownBy(() -> meetingService.approveJoinRequest(MEETING_ID, JOIN_REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEETING_CLOSED);

        verify(meeting, never()).increaseMemberCount();
        verify(meetingMemberRepository, never())
                .findPendingByIdAndMeetingIdForUpdate(JOIN_REQUEST_ID, MEETING_ID);
    }

    @Test
    @DisplayName("정원이 가득 찬 모임의 가입 신청은 승인할 수 없다")
    void approveJoinRequest_rejectsFullMeeting() {
        setAuthenticatedUser(HOST_ID);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meeting.getHost()).thenReturn(host);
        when(host.getId()).thenReturn(HOST_ID);
        when(meeting.isFull()).thenReturn(true);

        assertThatThrownBy(() -> meetingService.approveJoinRequest(MEETING_ID, JOIN_REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEETING_CLOSED);

        verify(meeting, never()).increaseMemberCount();
        verify(meetingMemberRepository, never())
                .findPendingByIdAndMeetingIdForUpdate(JOIN_REQUEST_ID, MEETING_ID);
    }

    @Test
    @DisplayName("가입 승인 후에도 실제 신청 시간은 유지된다")
    void approveJoinRequest_keepsRequestedAt() {
        setAuthenticatedUser(HOST_ID);
        MeetingMember member = pendingMember();
        LocalDateTime requestedAt = member.getJoinedAt();
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meeting.getHost()).thenReturn(host);
        when(host.getId()).thenReturn(HOST_ID);
        when(meeting.isFull()).thenReturn(false);
        when(meetingMemberRepository.findPendingByIdAndMeetingIdForUpdate(
                        JOIN_REQUEST_ID, MEETING_ID))
                .thenReturn(Optional.of(member));

        MeetingJoinRequestResponse response =
                meetingService.approveJoinRequest(MEETING_ID, JOIN_REQUEST_ID);

        assertThat(response.requestedAt()).isEqualTo(requestedAt);
    }

    @Test
    @DisplayName("가입 거절도 비관적 락으로 승인 대기 신청을 조회한다")
    void rejectJoinRequest_usesPendingRequestLock() {
        setAuthenticatedUser(HOST_ID);
        MeetingMember member = pendingMember();
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meeting.getHost()).thenReturn(host);
        when(host.getId()).thenReturn(HOST_ID);
        when(meetingMemberRepository.findPendingByIdAndMeetingIdForUpdate(
                        JOIN_REQUEST_ID, MEETING_ID))
                .thenReturn(Optional.of(member));

        MeetingJoinRequestResponse response =
                meetingService.rejectJoinRequest(MEETING_ID, JOIN_REQUEST_ID);

        assertThat(response.status()).isEqualTo(MeetingMemberStatus.REJECTED);
        verify(meeting, never()).increaseMemberCount();
    }

    private MeetingMember pendingMember() {
        return MeetingMember.createMember(user, meeting);
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
