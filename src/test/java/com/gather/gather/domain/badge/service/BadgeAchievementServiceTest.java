package com.gather.gather.domain.badge.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.badge.entity.Badge;
import com.gather.gather.domain.badge.entity.BadgeCode;
import com.gather.gather.domain.badge.repository.BadgeRepository;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BadgeAchievementServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long HOST_ID = 2L;

    @Mock private BadgeRepository badgeRepository;
    @Mock private UserBadgeRepository userBadgeRepository;
    @Mock private PostingParticipationRepository postingParticipationRepository;

    private BadgeAchievementService badgeAchievementService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        badgeAchievementService =
                new BadgeAchievementService(
                        badgeRepository, userBadgeRepository, postingParticipationRepository);
    }

    @Test
    @DisplayName("onParticipationCompleted awards FIRST_VOLUNTEER_COMPLETE on the first completion")
    void onParticipationCompleted_awardsFirstVolunteerBadge_onFirstCompletion() {
        when(postingParticipationRepository.findByUserIdAndStatusIn(eq(USER_ID), anyCollection()))
                .thenReturn(List.of(completedParticipation(10L)));
        when(badgeRepository.findByCode(BadgeCode.FIRST_VOLUNTEER_COMPLETE))
                .thenReturn(Optional.of(badge(1L, BadgeCode.FIRST_VOLUNTEER_COMPLETE)));
        when(userBadgeRepository.existsByUserIdAndBadgeId(USER_ID, 1L)).thenReturn(false);

        badgeAchievementService.onParticipationCompleted(USER_ID);

        verify(userBadgeRepository).saveAndFlush(any());
        verify(badgeRepository, never()).findByCode(BadgeCode.VOLUNTEER_5_COMPLETE);
    }

    @Test
    @DisplayName(
            "onParticipationCompleted awards VOLUNTEER_5_COMPLETE once the 5th activity completes")
    void onParticipationCompleted_awardsFifthVolunteerBadge_atFiveCompletions() {
        List<PostingParticipation> fiveCompleted =
                List.of(
                        completedParticipation(1L),
                        completedParticipation(2L),
                        completedParticipation(3L),
                        completedParticipation(4L),
                        completedParticipation(5L));
        when(postingParticipationRepository.findByUserIdAndStatusIn(eq(USER_ID), anyCollection()))
                .thenReturn(fiveCompleted);
        when(badgeRepository.findByCode(BadgeCode.FIRST_VOLUNTEER_COMPLETE))
                .thenReturn(Optional.of(badge(1L, BadgeCode.FIRST_VOLUNTEER_COMPLETE)));
        when(badgeRepository.findByCode(BadgeCode.VOLUNTEER_5_COMPLETE))
                .thenReturn(Optional.of(badge(2L, BadgeCode.VOLUNTEER_5_COMPLETE)));

        badgeAchievementService.onParticipationCompleted(USER_ID);

        verify(userBadgeRepository, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName("onParticipationCompleted does not award again when the badge is already earned")
    void onParticipationCompleted_skipsAward_whenAlreadyEarned() {
        when(postingParticipationRepository.findByUserIdAndStatusIn(eq(USER_ID), anyCollection()))
                .thenReturn(List.of(completedParticipation(10L)));
        when(badgeRepository.findByCode(BadgeCode.FIRST_VOLUNTEER_COMPLETE))
                .thenReturn(Optional.of(badge(1L, BadgeCode.FIRST_VOLUNTEER_COMPLETE)));
        when(userBadgeRepository.existsByUserIdAndBadgeId(USER_ID, 1L)).thenReturn(true);

        badgeAchievementService.onParticipationCompleted(USER_ID);

        verify(userBadgeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("onParticipationCompleted skips silently when the badge seed row is missing")
    void onParticipationCompleted_skipsSilently_whenBadgeSeedMissing() {
        when(postingParticipationRepository.findByUserIdAndStatusIn(eq(USER_ID), anyCollection()))
                .thenReturn(List.of(completedParticipation(10L)));
        when(badgeRepository.findByCode(BadgeCode.FIRST_VOLUNTEER_COMPLETE))
                .thenReturn(Optional.empty());

        badgeAchievementService.onParticipationCompleted(USER_ID);

        verify(userBadgeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName(
            "onInterestCategoriesUpdated awards the badge only when 3 or more categories are set")
    void onInterestCategoriesUpdated_awardsOnlyAtThreeOrMore() {
        when(badgeRepository.findByCode(BadgeCode.INTEREST_CATEGORY_3))
                .thenReturn(Optional.of(badge(3L, BadgeCode.INTEREST_CATEGORY_3)));

        badgeAchievementService.onInterestCategoriesUpdated(USER_ID, 2);
        verify(userBadgeRepository, never()).saveAndFlush(any());

        badgeAchievementService.onInterestCategoriesUpdated(USER_ID, 3);
        verify(userBadgeRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName(
            "onMeetingJoined awards TEAM_JOIN_FIRST to the joiner and TEAM_RECRUIT_SUCCESS to the"
                    + " host")
    void onMeetingJoined_awardsBothJoinerAndHostBadges() {
        when(badgeRepository.findByCode(BadgeCode.TEAM_JOIN_FIRST))
                .thenReturn(Optional.of(badge(4L, BadgeCode.TEAM_JOIN_FIRST)));
        when(badgeRepository.findByCode(BadgeCode.TEAM_RECRUIT_SUCCESS))
                .thenReturn(Optional.of(badge(5L, BadgeCode.TEAM_RECRUIT_SUCCESS)));

        badgeAchievementService.onMeetingJoined(USER_ID, HOST_ID);

        verify(userBadgeRepository, times(2)).saveAndFlush(any());
    }

    @Test
    @DisplayName(
            "onMeetingJoined does not award TEAM_RECRUIT_SUCCESS when the host joins their own meeting")
    void onMeetingJoined_skipsRecruitBadge_whenJoinerIsHost() {
        when(badgeRepository.findByCode(BadgeCode.TEAM_JOIN_FIRST))
                .thenReturn(Optional.of(badge(4L, BadgeCode.TEAM_JOIN_FIRST)));

        badgeAchievementService.onMeetingJoined(USER_ID, USER_ID);

        verify(badgeRepository, never()).findByCode(BadgeCode.TEAM_RECRUIT_SUCCESS);
    }

    /**
     * updatedAt은 {@code @UpdateTimestamp}로 Hibernate가 DB 반영 시점에만 채운다. 실제 조회 경로에서는 항상 값이 있지만, 순수
     * POJO로 만드는 테스트에서는 리플렉션으로 채워줘야 월별 집계(YearMonth.from) 중 NPE가 나지 않는다.
     */
    private PostingParticipation completedParticipation(Long postingId) {
        PostingParticipation participation = PostingParticipation.create(USER_ID, postingId);
        participation.complete();
        ReflectionTestUtils.setField(participation, "updatedAt", LocalDateTime.now());
        return participation;
    }

    private Badge badge(Long id, BadgeCode code) {
        Badge createdBadge;
        try {
            var constructor = Badge.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            createdBadge = constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("테스트용 Badge 생성 실패", exception);
        }
        ReflectionTestUtils.setField(createdBadge, "id", id);
        ReflectionTestUtils.setField(createdBadge, "code", code);
        return createdBadge;
    }
}
