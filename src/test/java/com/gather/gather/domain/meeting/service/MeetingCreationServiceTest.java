package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.dto.MeetingCreateRequest;
import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MeetingCreationServiceTest {

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
    @DisplayName("활동 기간이 없는 자유 모임을 생성한다")
    void createMeeting_createsFreeMeetingWithoutActivityPeriod() {
        setAuthenticatedUser(1L);
        User host = mock(User.class);
        MeetingCreateRequest request = createRequest(null, null, null);

        when(regionRepository.existsById(1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(host));
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(meetingMemberRepository.save(any(MeetingMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MeetingResponse response = meetingService.createMeeting(request);

        ArgumentCaptor<Meeting> meetingCaptor = ArgumentCaptor.forClass(Meeting.class);
        verify(meetingRepository).save(meetingCaptor.capture());
        Meeting savedMeeting = meetingCaptor.getValue();

        assertThat(savedMeeting.getVolunteerPostingId()).isNull();
        assertThat(savedMeeting.getActivityStartAt()).isNull();
        assertThat(savedMeeting.getActivityEndAt()).isNull();
        assertThat(response.activityStartAt()).isNull();
        verify(meetingMemberRepository).save(any(MeetingMember.class));
    }

    @Test
    @DisplayName("자유 모임은 카테고리를 최대 세 개까지 저장한다")
    void createMeeting_savesUpToThreeCategoriesForFreeMeeting() {
        setAuthenticatedUser(1L);
        User host = mock(User.class);
        Set<PostingCategory> categories =
                Set.of(
                        PostingCategory.ENVIRONMENT,
                        PostingCategory.EDUCATION,
                        PostingCategory.WELFARE);
        MeetingCreateRequest request = createRequest(null, null, null, categories);

        when(regionRepository.existsById(1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(host));
        when(meetingRepository.save(any(Meeting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(meetingMemberRepository.save(any(MeetingMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        meetingService.createMeeting(request);

        ArgumentCaptor<Meeting> meetingCaptor = ArgumentCaptor.forClass(Meeting.class);
        verify(meetingRepository).save(meetingCaptor.capture());
        assertThat(meetingCaptor.getValue().getCategories())
                .containsExactlyInAnyOrderElementsOf(categories);
    }

    @Test
    @DisplayName("자유 모임은 카테고리가 없으면 생성할 수 없다")
    void createMeeting_rejectsFreeMeetingWithoutCategory() {
        MeetingCreateRequest request = createRequest(null, null, null, Set.of());
        when(regionRepository.existsById(1L)).thenReturn(true);

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("공고 기반 모임은 활동 기간이 없으면 생성할 수 없다")
    void createMeeting_rejectsPostingBasedMeetingWithoutActivityPeriod() {
        MeetingCreateRequest request = createRequest(10L, null, null);

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_MEETING_TIME));
    }

    @Test
    @DisplayName("활동 시작 시간만 있으면 모임을 생성할 수 없다")
    void createMeeting_rejectsRequestWithOnlyActivityStartAt() {
        MeetingCreateRequest request = createRequest(10L, LocalDateTime.of(2026, 8, 2, 9, 0), null);

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_MEETING_TIME));
    }

    @Test
    @DisplayName("활동 종료 시간만 있으면 모임을 생성할 수 없다")
    void createMeeting_rejectsRequestWithOnlyActivityEndAt() {
        MeetingCreateRequest request =
                createRequest(10L, null, LocalDateTime.of(2026, 8, 2, 18, 0));

        assertThatThrownBy(() -> meetingService.createMeeting(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.INVALID_MEETING_TIME));
    }

    private MeetingCreateRequest createRequest(
            Long volunteerPostingId, LocalDateTime activityStartAt, LocalDateTime activityEndAt) {
        return createRequest(
                volunteerPostingId,
                activityStartAt,
                activityEndAt,
                Set.of(PostingCategory.ENVIRONMENT));
    }

    private MeetingCreateRequest createRequest(
            Long volunteerPostingId,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt,
            Set<PostingCategory> categories) {
        return new MeetingCreateRequest(
                "자유 모임",
                "활동 일정은 추후 등록합니다.",
                10,
                LocalDateTime.of(2026, 8, 1, 23, 59),
                null,
                categories,
                1L,
                "누구나 참여할 수 있습니다.",
                volunteerPostingId,
                activityStartAt,
                activityEndAt);
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
