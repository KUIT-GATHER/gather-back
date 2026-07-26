package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.auth.event.UserWithdrawnEvent;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 시 meeting 도메인 데이터를 정리한다.
 *
 * <p>리더십 위임 기능이 아직 없어, 탈퇴자가 모임장(host)인 활성 모임이 있으면 탈퇴 자체를 막는다(임시 정책 — 실제 정책은 연석님/PM 확인 필요). 그 외 가입
 * 승인 건은 탈퇴 처리하고, 대기 중인 가입 신청은 거절 처리한다.
 */
@Component
@RequiredArgsConstructor
public class MeetingWithdrawalListener {

    private final MeetingBookmarkRepository meetingBookmarkRepository;
    private final MeetingMemberRepository meetingMemberRepository;

    @EventListener
    @Transactional
    public void cleanUp(UserWithdrawnEvent event) {
        Long userId = event.userId();

        List<MeetingMember> approvedMemberships =
                meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        userId, MeetingMemberStatus.APPROVED);

        boolean isActiveHost =
                approvedMemberships.stream()
                        .anyMatch(member -> member.getMeeting().getHost().getId().equals(userId));
        if (isActiveHost) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_BLOCKED_MEETING_HOST);
        }

        approvedMemberships.forEach(MeetingMember::leave);

        meetingMemberRepository
                .findAllByUserIdAndStatusFetchMeeting(userId, MeetingMemberStatus.PENDING)
                .forEach(MeetingMember::reject);

        meetingBookmarkRepository.deleteAllByUserId(userId);
    }
}
