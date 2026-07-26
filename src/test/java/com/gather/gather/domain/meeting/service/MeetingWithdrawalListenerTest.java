package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.event.UserWithdrawnEvent;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingWithdrawalListenerTest {

    private static final Long USER_ID = 1L;

    @Mock private MeetingBookmarkRepository meetingBookmarkRepository;

    @Mock private MeetingMemberRepository meetingMemberRepository;

    private MeetingWithdrawalListener listener() {
        return new MeetingWithdrawalListener(meetingBookmarkRepository, meetingMemberRepository);
    }

    @Test
    @DisplayName("탈퇴자가 모임장인 활성 모임이 있으면 탈퇴를 막는다")
    void cleanUp_throws_whenUserIsActiveHost() {
        MeetingWithdrawalListener listener = listener();
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        Meeting hostedMeeting = mock(Meeting.class);
        when(hostedMeeting.getHost()).thenReturn(user);
        MeetingMember hostMembership = mock(MeetingMember.class);
        when(hostMembership.getMeeting()).thenReturn(hostedMeeting);
        when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of(hostMembership));

        assertThatThrownBy(() -> listener.cleanUp(new UserWithdrawnEvent(USER_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWAL_BLOCKED_MEETING_HOST);

        verify(meetingMemberRepository, never())
                .findAllByUserIdAndStatusFetchMeeting(USER_ID, MeetingMemberStatus.PENDING);
        verify(meetingBookmarkRepository, never()).deleteAllByUserId(any());
    }

    @Test
    @DisplayName("호스트가 아니면 가입 이력을 정리하고 북마크를 삭제한다")
    void cleanUp_leavesMembershipsAndDeletesBookmarks_whenNotHost() {
        MeetingWithdrawalListener listener = listener();
        User otherHost = mock(User.class);
        when(otherHost.getId()).thenReturn(999L);
        Meeting joinedMeeting = mock(Meeting.class);
        when(joinedMeeting.getHost()).thenReturn(otherHost);
        MeetingMember memberMembership = mock(MeetingMember.class);
        when(memberMembership.getMeeting()).thenReturn(joinedMeeting);
        when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of(memberMembership));
        when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        USER_ID, MeetingMemberStatus.PENDING))
                .thenReturn(List.of());

        listener.cleanUp(new UserWithdrawnEvent(USER_ID));

        verify(memberMembership).leave();
        verify(meetingBookmarkRepository).deleteAllByUserId(USER_ID);
    }
}
