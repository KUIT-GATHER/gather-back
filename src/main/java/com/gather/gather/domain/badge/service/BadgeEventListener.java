package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.meeting.service.MeetingCreatedEvent;
import com.gather.gather.domain.meeting.service.MeetingJoinedEvent;
import com.gather.gather.domain.post.service.ReviewPostedEvent;
import com.gather.gather.domain.posting.service.PostingParticipationCompletedEvent;
import com.gather.gather.domain.user.service.InterestCategoriesUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 다른 도메인(posting/user/meeting/post)이 발행한 이벤트를 구독해 뱃지를 판정한다(devplan2 8-2④). 뱃지 판정 실패가 원본 트랜잭션에 영향을
 * 주면 안 되므로 커밋 후(AFTER_COMMIT)에 별도 트랜잭션으로 실행하고, 실패는 로그만 남긴다(ProfileImageDeletionListener와 동일 패턴).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BadgeEventListener {

    private final BadgeAchievementService badgeAchievementService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleParticipationCompleted(PostingParticipationCompletedEvent event) {
        try {
            badgeAchievementService.onParticipationCompleted(event.userId());
        } catch (RuntimeException exception) {
            log.warn("참여완료 뱃지 판정 실패. userId={}", event.userId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInterestCategoriesUpdated(InterestCategoriesUpdatedEvent event) {
        try {
            badgeAchievementService.onInterestCategoriesUpdated(
                    event.userId(), event.categoryCount());
        } catch (RuntimeException exception) {
            log.warn("관심분야 뱃지 판정 실패. userId={}", event.userId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMeetingCreated(MeetingCreatedEvent event) {
        try {
            badgeAchievementService.onMeetingCreated(event.userId());
        } catch (RuntimeException exception) {
            log.warn("모임생성 뱃지 판정 실패. userId={}", event.userId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMeetingJoined(MeetingJoinedEvent event) {
        try {
            badgeAchievementService.onMeetingJoined(event.joinedUserId(), event.hostUserId());
        } catch (RuntimeException exception) {
            log.warn("모임가입 뱃지 판정 실패. joinedUserId={}", event.joinedUserId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReviewPosted(ReviewPostedEvent event) {
        try {
            badgeAchievementService.onReviewPosted(event.userId());
        } catch (RuntimeException exception) {
            log.warn("후기작성 뱃지 판정 실패. userId={}", event.userId(), exception);
        }
    }
}
