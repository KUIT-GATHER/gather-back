package com.gather.gather.domain.badge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BadgeQueryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private BadgeEvaluationService badgeEvaluationService;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostCommentRepository postCommentRepository;

    private BadgeQueryService badgeQueryService;

    @BeforeEach
    void setUp() {
        badgeQueryService =
                new BadgeQueryService(
                        userBadgeRepository,
                        badgeEvaluationService,
                        bookmarkRepository,
                        meetingMemberRepository,
                        postRepository,
                        postCommentRepository);

        lenient()
                .when(userBadgeRepository.findAllByUserIdOrderByEarnedAtDesc(USER_ID))
                .thenReturn(List.of());
        lenient()
                .when(badgeEvaluationService.collectCompletionDates(USER_ID))
                .thenReturn(List.of());
        lenient()
                .when(badgeEvaluationService.longestConsecutiveMonthStreak(List.of()))
                .thenReturn(0);
        lenient()
                .when(
                        meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                                USER_ID, MeetingMemberStatus.APPROVED))
                .thenReturn(List.of());
        lenient().when(bookmarkRepository.countByUserId(USER_ID)).thenReturn(0L);
        lenient()
                .when(
                        postRepository.countByUser_IdAndTypeAndDeletedAtIsNull(
                                USER_ID, PostType.REVIEW))
                .thenReturn(0L);
        lenient()
                .when(postCommentRepository.countByUser_IdAndDeletedAtIsNull(USER_ID))
                .thenReturn(0L);
    }

    @Test
    @DisplayName("getMyBadges returns all 8 badge types even when the user has earned nothing")
    void getMyBadges_returnsAllEightBadgeTypes_whenNoneEarned() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);

            List<BadgeStatusResponse> badges = badgeQueryService.getMyBadges();

            assertThat(badges).hasSize(BadgeType.values().length);
            assertThat(badges).allMatch(badge -> !badge.earned());
            assertThat(badges).allMatch(badge -> badge.earnedAt() == null);
        }
    }

    @Test
    @DisplayName(
            "getMyBadges marks an earned badge with earned=true, its earnedAt, and"
                    + " currentValue=targetValue regardless of live progress")
    void getMyBadges_marksEarnedBadgeWithEarnedAtAndFullProgress() {
        LocalDateTime earnedAt = LocalDateTime.of(2026, 7, 1, 12, 0);
        UserBadge userBadge = UserBadge.create(USER_ID, BadgeType.BOOKMARK_5);
        org.springframework.test.util.ReflectionTestUtils.setField(userBadge, "earnedAt", earnedAt);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userBadgeRepository.findAllByUserIdOrderByEarnedAtDesc(USER_ID))
                    .thenReturn(List.of(userBadge));
            // 뱃지를 획득한 뒤 북마크를 해제해도(진행 수치가 목표 아래로 내려가도) earned 카드는 여전히 만점을 보여줘야 한다.
            when(bookmarkRepository.countByUserId(USER_ID)).thenReturn(2L);

            List<BadgeStatusResponse> badges = badgeQueryService.getMyBadges();

            BadgeStatusResponse bookmarkBadge =
                    badges.stream()
                            .filter(badge -> badge.badgeType() == BadgeType.BOOKMARK_5)
                            .findFirst()
                            .orElseThrow();
            assertThat(bookmarkBadge.earned()).isTrue();
            assertThat(bookmarkBadge.earnedAt()).isEqualTo(earnedAt);
            assertThat(bookmarkBadge.currentValue()).isEqualTo(bookmarkBadge.targetValue());
        }
    }

    @Test
    @DisplayName(
            "getMyBadges computes FIRST_COMPLETION/COMPLETION_5 currentValue from the shared"
                    + " completion date list, capped at each badge's targetValue")
    void getMyBadges_computesCompletionProgressFromSharedCompletionDates() {
        List<LocalDate> completionDates =
                List.of(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 11),
                        LocalDate.of(2026, 1, 12));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(badgeEvaluationService.collectCompletionDates(USER_ID))
                    .thenReturn(completionDates);
            when(badgeEvaluationService.longestConsecutiveMonthStreak(completionDates))
                    .thenReturn(1);

            List<BadgeStatusResponse> badges = badgeQueryService.getMyBadges();

            assertThat(currentValueOf(badges, BadgeType.FIRST_COMPLETION)).isEqualTo(1);
            assertThat(currentValueOf(badges, BadgeType.COMPLETION_5)).isEqualTo(3);
        }
    }

    @Test
    @DisplayName(
            "getMyBadges caps COMPLETION_5 currentValue at its targetValue even when the raw"
                    + " completion count exceeds it")
    void getMyBadges_capsCompletionProgressAtTargetValue() {
        List<LocalDate> completionDates =
                List.of(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 11),
                        LocalDate.of(2026, 1, 12),
                        LocalDate.of(2026, 1, 13),
                        LocalDate.of(2026, 1, 14),
                        LocalDate.of(2026, 1, 15),
                        LocalDate.of(2026, 1, 16));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(badgeEvaluationService.collectCompletionDates(USER_ID))
                    .thenReturn(completionDates);
            when(badgeEvaluationService.longestConsecutiveMonthStreak(completionDates))
                    .thenReturn(1);

            List<BadgeStatusResponse> badges = badgeQueryService.getMyBadges();

            assertThat(currentValueOf(badges, BadgeType.COMPLETION_5)).isEqualTo(5);
        }
    }

    @Test
    @DisplayName(
            "getMyBadges sets FIRST_TEAM_JOIN currentValue=1 only when an approved MEMBER-role"
                    + " membership exists, independent of TEAM_CREATED (HOST-role)")
    void getMyBadges_distinguishesTeamJoinFromTeamCreatedByRole() {
        MeetingMember memberMembership = mock(MeetingMember.class);
        when(memberMembership.getRole()).thenReturn(MeetingMemberRole.MEMBER);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                            USER_ID, MeetingMemberStatus.APPROVED))
                    .thenReturn(List.of(memberMembership));

            List<BadgeStatusResponse> badges = badgeQueryService.getMyBadges();

            assertThat(currentValueOf(badges, BadgeType.FIRST_TEAM_JOIN)).isEqualTo(1);
            assertThat(currentValueOf(badges, BadgeType.TEAM_CREATED)).isEqualTo(0);
        }
    }

    @Test
    @DisplayName(
            "getMyBadges sets TEAM_CREATED currentValue=1 when an approved HOST membership exists")
    void getMyBadges_setsTeamCreatedProgress_whenHostMembershipExists() {
        MeetingMember hostMembership = mock(MeetingMember.class);
        when(hostMembership.getRole()).thenReturn(MeetingMemberRole.HOST);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(meetingMemberRepository.findAllByUserIdAndStatusFetchMeeting(
                            USER_ID, MeetingMemberStatus.APPROVED))
                    .thenReturn(List.of(hostMembership));

            List<BadgeStatusResponse> badges = badgeQueryService.getMyBadges();

            assertThat(currentValueOf(badges, BadgeType.TEAM_CREATED)).isEqualTo(1);
            assertThat(currentValueOf(badges, BadgeType.FIRST_TEAM_JOIN)).isEqualTo(0);
        }
    }

    @Test
    @DisplayName(
            "getMyBadges computes BOOKMARK_5/FIRST_REVIEW/COMMENT_10 currentValue from their"
                    + " respective repository counts")
    void getMyBadges_computesProgressFromRepositoryCounts() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(bookmarkRepository.countByUserId(USER_ID)).thenReturn(3L);
            when(postRepository.countByUser_IdAndTypeAndDeletedAtIsNull(USER_ID, PostType.REVIEW))
                    .thenReturn(1L);
            when(postCommentRepository.countByUser_IdAndDeletedAtIsNull(USER_ID)).thenReturn(4L);

            List<BadgeStatusResponse> badges = badgeQueryService.getMyBadges();

            assertThat(currentValueOf(badges, BadgeType.BOOKMARK_5)).isEqualTo(3);
            assertThat(currentValueOf(badges, BadgeType.FIRST_REVIEW)).isEqualTo(1);
            assertThat(currentValueOf(badges, BadgeType.COMMENT_10)).isEqualTo(4);
        }
    }

    private int currentValueOf(List<BadgeStatusResponse> badges, BadgeType badgeType) {
        return badges.stream()
                .filter(badge -> badge.badgeType() == badgeType)
                .findFirst()
                .orElseThrow()
                .currentValue();
    }
}
