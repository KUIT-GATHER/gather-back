package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.dto.PostingMeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.notification.service.NotificationCreateService;
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
    @Mock private NotificationCreateService notificationCreateService;

    @InjectMocks private MeetingService meetingService;

    private Meeting meeting;

    @BeforeEach
    void setUp() {
        meeting = mock(Meeting.class);
        when(meeting.getId()).thenReturn(12L);
        when(meeting.getName()).thenReturn("한강공원 플로깅팀");
        when(meeting.getCategories()).thenReturn(Set.of(PostingCategory.ENVIRONMENT));
        when(meeting.getCurrentMemberCount()).thenReturn(12);
        when(meeting.getMaxMember()).thenReturn(20);
        when(meeting.getStatus()).thenReturn(MeetingStatus.RECRUITING);
        when(meeting.isActivityEnded(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(meeting.isDeadlinePassed(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(meeting.isFull()).thenReturn(false);

        when(postingRepository.existsById(10L)).thenReturn(true);
        when(meetingRepository.findAllByVolunteerPostingIdAndDeletedAtIsNull(
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
