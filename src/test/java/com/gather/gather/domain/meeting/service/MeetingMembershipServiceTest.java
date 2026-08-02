package com.gather.gather.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingMembershipServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MEETING_ID = 10L;

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;

    @InjectMocks private MeetingMembershipService meetingMembershipService;

    private MockedStatic<SecurityUtil> securityUtil;

    @BeforeEach
    void setUp() {
        securityUtil = Mockito.mockStatic(SecurityUtil.class);
        securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtil.close();
    }

    @Test
    @DisplayName("팀원은 모임을 나가고 상태 변경·인원 감소가 일어난다")
    void leaveMeeting_memberLeavesAndCountDecreases() {
        Meeting meeting = Mockito.mock(Meeting.class);
        MeetingMember membership = member(MeetingMemberRole.MEMBER);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(membership));

        meetingMembershipService.leaveMeeting(MEETING_ID);

        verify(membership).leave();
        verify(meeting).decreaseMemberCount();
    }

    @Test
    @DisplayName("팀장은 모임을 나갈 수 없다")
    void leaveMeeting_rejectsHost() {
        Meeting meeting = Mockito.mock(Meeting.class);
        MeetingMember membership = member(MeetingMemberRole.HOST);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> meetingMembershipService.leaveMeeting(MEETING_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_HOST_CANNOT_LEAVE);
        verify(membership, never()).leave();
        verify(meeting, never()).decreaseMemberCount();
    }

    @Test
    @DisplayName("가입 상태가 아니면 나가기를 거부한다")
    void leaveMeeting_rejectsNonMember() {
        Meeting meeting = Mockito.mock(Meeting.class);
        when(meetingRepository.findByIdAndDeletedAtIsNullForUpdate(MEETING_ID))
                .thenReturn(Optional.of(meeting));
        when(meetingMemberRepository.findByMeeting_IdAndUser_IdAndStatus(
                        MEETING_ID, USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingMembershipService.leaveMeeting(MEETING_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEETING_MEMBER_REQUIRED);
    }

    private MeetingMember member(MeetingMemberRole role) {
        MeetingMember member = Mockito.mock(MeetingMember.class);
        when(member.getRole()).thenReturn(role);
        return member;
    }
}
