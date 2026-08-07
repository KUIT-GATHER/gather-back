package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.meeting.dto.MeetingHomeResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
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
class MeetingHomeServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private PostingRepository postingRepository;

    @InjectMocks private MeetingHomeService meetingHomeService;

    private Meeting meeting;

    @BeforeEach
    void setUp() {
        meeting = mock(Meeting.class);
        org.mockito.Mockito.lenient().when(meeting.getId()).thenReturn(12L);
        org.mockito.Mockito.lenient().when(meeting.getName()).thenReturn("한강공원 플로깅팀");
        org.mockito.Mockito.lenient().when(meeting.getDescription()).thenReturn("소개");
        org.mockito.Mockito.lenient()
                .when(meeting.getDeadline())
                .thenReturn(java.time.LocalDateTime.now().plusDays(5));
        org.mockito.Mockito.lenient().when(meeting.getRegionId()).thenReturn(5L);
        org.mockito.Mockito.lenient().when(meeting.getCurrentMemberCount()).thenReturn(3);
        org.mockito.Mockito.lenient().when(meeting.getMaxMember()).thenReturn(20);
        org.mockito.Mockito.lenient()
                .when(meeting.getStatus())
                .thenReturn(MeetingStatus.RECRUITING);
        org.mockito.Mockito.lenient().when(meeting.isActivityEnded(any())).thenReturn(false);
        org.mockito.Mockito.lenient().when(meeting.isDeadlinePassed(any())).thenReturn(false);
        org.mockito.Mockito.lenient().when(meeting.isFull()).thenReturn(false);
        org.mockito.Mockito.lenient().when(meeting.getVolunteerPostingId()).thenReturn(null);
        org.mockito.Mockito.lenient().when(meeting.getParticipationCondition()).thenReturn(null);

        when(meetingRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(meeting));
        org.mockito.Mockito.lenient()
                .when(
                        meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                                12L, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(regionRepository.findById(anyLong()))
                .thenReturn(Optional.empty());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName(
            "getMeetingHome returns pendingJoinRequested=true with the request id when the caller"
                    + " has a pending join request")
    void getMeetingHome_returnsPendingTrue_whenCallerHasPendingRequest() {
        setAuthenticatedUser(1L);
        MeetingMember pendingMember = mock(MeetingMember.class);
        when(pendingMember.getId()).thenReturn(77L);
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        12L, 1L, MeetingMemberStatus.PENDING))
                .thenReturn(Optional.of(pendingMember));

        MeetingHomeResponse response = meetingHomeService.getMeetingHome(12L);

        assertThat(response.pendingJoinRequested()).isTrue();
        assertThat(response.myPendingJoinRequestId()).isEqualTo(77L);
    }

    @Test
    @DisplayName(
            "getMeetingHome returns pendingJoinRequested=false when the caller has no pending"
                    + " join request")
    void getMeetingHome_returnsPendingFalse_whenNoPendingRequest() {
        setAuthenticatedUser(1L);
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        12L, 1L, MeetingMemberStatus.PENDING))
                .thenReturn(Optional.empty());

        MeetingHomeResponse response = meetingHomeService.getMeetingHome(12L);

        assertThat(response.pendingJoinRequested()).isFalse();
        assertThat(response.myPendingJoinRequestId()).isNull();
    }

    @Test
    @DisplayName(
            "getMeetingHome returns pendingJoinRequested=false for an anonymous user without"
                    + " querying membership")
    void getMeetingHome_returnsPendingFalse_forAnonymousUser() {
        MeetingHomeResponse response = meetingHomeService.getMeetingHome(12L);

        assertThat(response.pendingJoinRequested()).isFalse();
        assertThat(response.myPendingJoinRequestId()).isNull();
        verify(meetingMemberRepository, never())
                .findByMeeting_IdAndUser_IdAndStatus(
                        anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
