package com.gather.gather.domain.badge.listener;

import com.gather.gather.domain.badge.event.BadgeAwardRequestedEvent;
import com.gather.gather.domain.badge.event.MeetingCompletedEvent;
import com.gather.gather.domain.badge.event.VolunteerActivityCompletedEvent;
import com.gather.gather.domain.badge.service.BadgeAwardService;
import com.gather.gather.domain.badge.service.BadgeEvaluationService;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 뱃지 지급/평가는 본 비즈니스 트랜잭션 커밋 이후(AFTER_COMMIT)에 별도로 처리한다.
 *
 * <p>뱃지 지급이 REQUIRED 전파로 호출자 트랜잭션에 참여하면, 뱃지 저장 실패가 catch되더라도 이미 rollbackOnly로 마킹된 트랜잭션이 커밋 시점에 본
 * 처리(완료 처리·북마크·모임 생성 등)까지 통째로 롤백시킨다. AFTER_COMMIT으로 분리하면 본 처리는 이미 커밋된 뒤이므로, 여기서 발생하는 예외는 본 처리에 영향을
 * 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BadgeEventListener {

    private final BadgeAwardService badgeAwardService;
    private final BadgeEvaluationService badgeEvaluationService;
    private final MeetingMemberRepository meetingMemberRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBadgeAwardRequested(BadgeAwardRequestedEvent event) {
        try {
            badgeAwardService.award(event.userId(), event.badgeType());
        } catch (RuntimeException exception) {
            log.warn(
                    "뱃지 지급 실패(본 처리는 이미 커밋되어 유지됨). userId={}, badgeType={}",
                    event.userId(),
                    event.badgeType(),
                    exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVolunteerActivityCompleted(VolunteerActivityCompletedEvent event) {
        try {
            badgeEvaluationService.onVolunteerActivityCompleted(event.userId());
        } catch (RuntimeException exception) {
            log.warn("봉사 완료 뱃지 판정 실패(완료 처리는 이미 커밋되어 유지됨). userId={}", event.userId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMeetingCompleted(MeetingCompletedEvent event) {
        for (MeetingMember member :
                meetingMemberRepository.findAllByMeetingIdAndStatusFetchUser(
                        event.meetingId(), MeetingMemberStatus.APPROVED)) {
            evaluateMemberBadgeSafely(event.meetingId(), member);
        }
    }

    /** 멤버 한 명의 뱃지 판정 실패가 나머지 멤버의 판정을 막지 않도록 각 멤버를 독립된 트랜잭션·예외 범위로 격리한다. */
    private void evaluateMemberBadgeSafely(Long meetingId, MeetingMember member) {
        Long memberUserId = member.getUser().getId();
        try {
            badgeEvaluationService.onVolunteerActivityCompleted(memberUserId);
        } catch (RuntimeException exception) {
            log.warn(
                    "모임 완료 후 뱃지 판정 실패(해당 멤버만 영향, 모임 완료 처리는 이미 커밋되어 유지됨). meetingId={}, userId={}",
                    meetingId,
                    memberUserId,
                    exception);
        }
    }
}
