package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.dto.MeetingDetailResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MeetingDetailBookmarkServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingBookmarkRepository meetingBookmarkRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private MeetingSearchLogService meetingSearchLogService;

    @InjectMocks private MeetingService meetingService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("비로그인 모임 상세 조회는 bookmarked가 false다")
    void getMeeting_returnsNotBookmarkedForAnonymousUser() {
        Meeting meeting = createMeeting();
        when(meetingRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(meeting));

        MeetingDetailResponse response = meetingService.getMeeting(12L);

        assertThat(response.bookmarked()).isFalse();
        verify(meetingBookmarkRepository, never()).existsByUserIdAndMeetingId(1L, 12L);
    }

    @Test
    @DisplayName("로그인 사용자가 북마크한 모임이면 bookmarked가 true다")
    void getMeeting_returnsBookmarkedForAuthenticatedUser() {
        setAuthenticatedUser(1L);
        Meeting meeting = createMeeting();
        when(meetingRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(meeting));
        when(meetingBookmarkRepository.existsByUserIdAndMeetingId(1L, 12L)).thenReturn(true);

        MeetingDetailResponse response = meetingService.getMeeting(12L);

        assertThat(response.bookmarked()).isTrue();
    }

    @Test
    @DisplayName("로그인 사용자가 북마크하지 않은 모임이면 bookmarked가 false다")
    void getMeeting_returnsNotBookmarkedForAuthenticatedUser() {
        setAuthenticatedUser(1L);
        Meeting meeting = createMeeting();
        when(meetingRepository.findByIdAndDeletedAtIsNull(12L)).thenReturn(Optional.of(meeting));
        when(meetingBookmarkRepository.existsByUserIdAndMeetingId(1L, 12L)).thenReturn(false);

        MeetingDetailResponse response = meetingService.getMeeting(12L);

        assertThat(response.bookmarked()).isFalse();
    }

    private Meeting createMeeting() {
        Meeting meeting = mock(Meeting.class);
        User host = mock(User.class);
        when(host.getId()).thenReturn(2L);
        when(meeting.getHost()).thenReturn(host);
        when(meeting.getStatus()).thenReturn(MeetingStatus.RECRUITING);
        when(meeting.isActivityEnded(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(meeting.isDeadlinePassed(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(meeting.isFull()).thenReturn(false);
        return meeting;
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
