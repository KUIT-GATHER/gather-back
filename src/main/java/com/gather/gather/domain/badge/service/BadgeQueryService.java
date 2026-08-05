package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.badge.dto.BadgeStatusResponse;
import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.entity.UserBadge;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberRole;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.repository.PostCommentRepository;
import com.gather.gather.domain.post.repository.PostRepository;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지 활동 뱃지 화면 - 8종 뱃지 전체(획득 + 잠긴 뱃지)의 현재 상태를 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BadgeQueryService {

    private final UserBadgeRepository userBadgeRepository;
    private final BadgeEvaluationService badgeEvaluationService;
    private final BookmarkRepository bookmarkRepository;
    private final MeetingMemberRepository meetingMemberRepository;
    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;

    public List<BadgeStatusResponse> getMyBadges() {
        Long userId = SecurityUtil.getCurrentUserId();

        Map<BadgeType, UserBadge> earnedByType =
                userBadgeRepository.findAllByUserIdOrderByEarnedAtDesc(userId).stream()
                        .collect(Collectors.toMap(UserBadge::getBadgeType, badge -> badge));

        Map<BadgeType, Integer> currentValues = resolveCurrentValues(userId);

        return Arrays.stream(BadgeType.values())
                .map(
                        badgeType -> {
                            UserBadge earned = earnedByType.get(badgeType);
                            if (earned != null) {
                                return BadgeStatusResponse.earned(earned);
                            }
                            return BadgeStatusResponse.locked(
                                    badgeType, currentValues.getOrDefault(badgeType, 0));
                        })
                .toList();
    }

    /** 잠긴 뱃지의 진행률 표시를 위한 currentValue를 badgeType별로 한 번씩만 계산한다. */
    private Map<BadgeType, Integer> resolveCurrentValues(Long userId) {
        List<LocalDate> completionDates = badgeEvaluationService.collectCompletionDates(userId);
        int completedCount = completionDates.size();
        int consecutiveMonthStreak =
                badgeEvaluationService.longestConsecutiveMonthStreak(completionDates);

        List<MeetingMember> approvedMemberships =
                meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                        userId, MeetingMemberStatus.APPROVED);
        boolean hasJoinedTeam =
                approvedMemberships.stream()
                        .anyMatch(member -> member.getRole() == MeetingMemberRole.MEMBER);
        boolean hasCreatedTeam =
                approvedMemberships.stream()
                        .anyMatch(member -> member.getRole() == MeetingMemberRole.HOST);

        long bookmarkCount = bookmarkRepository.countByUserId(userId);
        long reviewCount =
                postRepository.countByUser_IdAndTypeAndDeletedAtIsNull(userId, PostType.REVIEW);
        long commentCount = postCommentRepository.countByUser_IdAndDeletedAtIsNull(userId);

        return Map.of(
                BadgeType.FIRST_COMPLETION, capAt(completedCount, BadgeType.FIRST_COMPLETION),
                BadgeType.COMPLETION_5, capAt(completedCount, BadgeType.COMPLETION_5),
                BadgeType.CONSECUTIVE_3_MONTHS,
                        capAt(consecutiveMonthStreak, BadgeType.CONSECUTIVE_3_MONTHS),
                BadgeType.BOOKMARK_5, capAt((int) bookmarkCount, BadgeType.BOOKMARK_5),
                BadgeType.FIRST_TEAM_JOIN, hasJoinedTeam ? 1 : 0,
                BadgeType.TEAM_CREATED, hasCreatedTeam ? 1 : 0,
                BadgeType.FIRST_REVIEW, capAt((int) reviewCount, BadgeType.FIRST_REVIEW),
                BadgeType.COMMENT_10, capAt((int) commentCount, BadgeType.COMMENT_10));
    }

    /** 진행 수치가 목표치를 넘지 않도록 캡한다(목표 도달 즉시 뱃지가 획득 처리되므로 잠긴 뱃지에서는 항상 목표치 미만이어야 정상이다). */
    private int capAt(int value, BadgeType badgeType) {
        return Math.min(value, badgeType.getTargetValue());
    }
}
