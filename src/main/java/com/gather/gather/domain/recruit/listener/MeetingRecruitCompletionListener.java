package com.gather.gather.domain.recruit.listener;

import com.gather.gather.domain.badge.event.MeetingCompletedEvent;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import com.gather.gather.domain.recruit.repository.MeetingRecruitParticipationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 모임 완료 처리 커밋 이후, 해당 모임의 모집공고 참여를 봉사완료로 일괄 전환한다.
 *
 * <p>{@code BadgeEventListener}와 동일하게 AFTER_COMMIT으로 분리한다 — 모임 완료 처리는 이미 커밋된 뒤이므로 여기서의 실패가 본 처리에
 * 영향을 주지 않는다. 새 트랜잭션(REQUIRES_NEW)에서 벌크 업데이트를 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingRecruitCompletionListener {

    private final MeetingRecruitParticipationRepository participationRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMeetingCompleted(MeetingCompletedEvent event) {
        try {
            int updated =
                    participationRepository.updateStatusByMeeting(
                            event.meetingId(),
                            MeetingRecruitParticipationStatus.APPLIED,
                            MeetingRecruitParticipationStatus.COMPLETED,
                            PostType.RECRUIT);
            log.info("모임 완료로 모집공고 참여 {}건을 봉사완료로 전환했습니다. meetingId={}", updated, event.meetingId());
        } catch (RuntimeException exception) {
            log.warn(
                    "모집공고 참여 봉사완료 전환 실패(모임 완료 처리는 이미 커밋되어 유지됨). meetingId={}",
                    event.meetingId(),
                    exception);
        }
    }
}
