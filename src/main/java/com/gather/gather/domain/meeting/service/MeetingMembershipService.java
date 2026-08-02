package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.meeting.repository.MeetingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 가입/탈퇴 등 멤버십 상태 전이.
 *
 * <p>모임 나가기: 팀원(MEMBER)만 가능하며 팀장(HOST)은 나갈 수 없다(모임 위임/삭제로 처리). 나가면 멤버십 상태를 LEFT로 바꾸고 모임 현재
 * 인원을 1 감소시킨다. 재가입 시 기존 행을 재사용한다(uk_meeting_member_user_meeting).
 */
@Service
@RequiredArgsConstructor
public class MeetingMembershipService {

    private final MeetingRepository meetingRepository;
    private final MeetingMemberRepository meetingMemberRepository;

    @Transactional
    public void leaveMeeting(Long meetingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        Meeting meeting =
                meetingRepository
                        .findByIdAndDeletedAtIsNullForUpdate(meetingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));

        MeetingMember membership =
                meetingMemberRepository
                        .findByMeeting_IdAndUser_IdAndStatus(
                                meetingId, userId, MeetingMemberStatus.APPROVED)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.MEETING_MEMBER_REQUIRED));

        if (membership.getRole() == MeetingMemberRole.HOST) {
            throw new BusinessException(ErrorCode.MEETING_HOST_CANNOT_LEAVE);
        }

        membership.leave();
        meeting.decreaseMemberCount();
    }
}
