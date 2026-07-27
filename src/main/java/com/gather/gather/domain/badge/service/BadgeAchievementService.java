package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.badge.entity.Badge;
import com.gather.gather.domain.badge.entity.BadgeCode;
import com.gather.gather.domain.badge.entity.UserBadge;
import com.gather.gather.domain.badge.repository.BadgeRepository;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 뱃지 8종(devplan2 8-2②)의 달성 판정. 기준이 도메인마다 이질적이라 code별 전용 메서드로 나눈다(범용 기준 엔진은 과설계로 판단해 배제). */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BadgeAchievementService {

    private static final int VOLUNTEER_MILESTONE_5 = 5;
    private static final int MONTHLY_MILESTONE = 2;
    private static final int INTEREST_CATEGORY_MILESTONE = 3;

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final PostingParticipationRepository postingParticipationRepository;

    public void onParticipationCompleted(Long userId) {
        List<PostingParticipation> completed =
                postingParticipationRepository.findByUserIdAndStatusIn(
                        userId, EnumSet.of(PostingParticipationStatus.COMPLETED));

        if (!completed.isEmpty()) {
            award(userId, BadgeCode.FIRST_VOLUNTEER_COMPLETE);
        }
        if (completed.size() >= VOLUNTEER_MILESTONE_5) {
            award(userId, BadgeCode.VOLUNTEER_5_COMPLETE);
        }

        YearMonth currentMonth = YearMonth.now();
        long completedThisMonth =
                completed.stream()
                        .filter(
                                participation ->
                                        YearMonth.from(participation.getUpdatedAt())
                                                .equals(currentMonth))
                        .count();
        if (completedThisMonth >= MONTHLY_MILESTONE) {
            award(userId, BadgeCode.MONTHLY_2_PARTICIPATION);
        }
    }

    public void onInterestCategoriesUpdated(Long userId, int categoryCount) {
        if (categoryCount >= INTEREST_CATEGORY_MILESTONE) {
            award(userId, BadgeCode.INTEREST_CATEGORY_3);
        }
    }

    public void onMeetingCreated(Long userId) {
        award(userId, BadgeCode.TEAM_CREATE_FIRST);
    }

    /** 가입 확정 시 가입자와 모임장 양쪽 뱃지를 함께 판정한다(devplan2 8-2② #3/#5는 같은 이벤트의 서로 다른 관점). */
    public void onMeetingJoined(Long joinedUserId, Long hostUserId) {
        award(joinedUserId, BadgeCode.TEAM_JOIN_FIRST);
        if (!joinedUserId.equals(hostUserId)) {
            award(hostUserId, BadgeCode.TEAM_RECRUIT_SUCCESS);
        }
    }

    public void onReviewPosted(Long userId) {
        award(userId, BadgeCode.REVIEW_WRITE_FIRST);
    }

    private void award(Long userId, BadgeCode code) {
        Badge badge = badgeRepository.findByCode(code).orElse(null);
        if (badge == null) {
            log.warn("뱃지 시드 데이터를 찾을 수 없음. code={}", code);
            return;
        }
        if (userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
            return;
        }
        try {
            userBadgeRepository.saveAndFlush(UserBadge.create(userId, badge.getId()));
        } catch (DataIntegrityViolationException exception) {
            log.debug("뱃지 중복 획득 시도(동시성). userId={}, code={}", userId, code, exception);
        }
    }
}
