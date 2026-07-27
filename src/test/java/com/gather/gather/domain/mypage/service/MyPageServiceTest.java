package com.gather.gather.domain.mypage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.badge.entity.Badge;
import com.gather.gather.domain.badge.entity.BadgeCode;
import com.gather.gather.domain.badge.entity.UserBadge;
import com.gather.gather.domain.badge.repository.BadgeRepository;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.mypage.dto.MyPageActivityRecordResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivityResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivitySummaryResponse;
import com.gather.gather.domain.mypage.dto.MyPageBadgeSummaryResponse;
import com.gather.gather.domain.mypage.dto.MyPageHomeResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.user.service.ProfileImageUrlResolver;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private MeetingBookmarkRepository meetingBookmarkRepository;
    @Mock private PostingParticipationRepository postingParticipationRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private ProfileImageUrlResolver profileImageUrlResolver;
    @Mock private BadgeRepository badgeRepository;
    @Mock private UserBadgeRepository userBadgeRepository;

    private MyPageService myPageService;

    @BeforeEach
    void setUp() {
        myPageService =
                new MyPageService(
                        userRepository,
                        bookmarkRepository,
                        meetingBookmarkRepository,
                        postingParticipationRepository,
                        postingRepository,
                        profileImageUrlResolver,
                        badgeRepository,
                        userBadgeRepository);
    }

    @Test
    @DisplayName(
            "getHome returns profile summary with hasBookmark=true when a posting bookmark exists")
    void getHome_returnsHasBookmarkTrue_whenPostingBookmarkExists() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(bookmarkRepository.existsByUserId(USER_ID)).thenReturn(true);
            when(profileImageUrlResolver.resolve(null)).thenReturn(null);

            MyPageHomeResponse response = myPageService.getHome();

            assertThat(response.nickname()).isEqualTo("길동");
            assertThat(response.hasBookmark()).isTrue();
            verify(meetingBookmarkRepository, never()).existsByUserId(USER_ID);
        }
    }

    @Test
    @DisplayName(
            "getHome returns hasBookmark=true when only a meeting bookmark exists (no posting"
                    + " bookmark)")
    void getHome_returnsHasBookmarkTrue_whenOnlyMeetingBookmarkExists() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(bookmarkRepository.existsByUserId(USER_ID)).thenReturn(false);
            when(meetingBookmarkRepository.existsByUserId(USER_ID)).thenReturn(true);
            when(profileImageUrlResolver.resolve(null)).thenReturn(null);

            MyPageHomeResponse response = myPageService.getHome();

            assertThat(response.hasBookmark()).isTrue();
        }
    }

    @Test
    @DisplayName(
            "getHome returns hasBookmark=false when neither posting nor meeting bookmarks exist")
    void getHome_returnsHasBookmarkFalse_whenNoBookmarksExist() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(bookmarkRepository.existsByUserId(USER_ID)).thenReturn(false);
            when(meetingBookmarkRepository.existsByUserId(USER_ID)).thenReturn(false);
            when(profileImageUrlResolver.resolve(null)).thenReturn(null);

            MyPageHomeResponse response = myPageService.getHome();

            assertThat(response.hasBookmark()).isFalse();
        }
    }

    @Test
    @DisplayName("getHome throws USER_NOT_FOUND when the user no longer exists")
    void getHome_throwsUserNotFound_whenMissing() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> myPageService.getHome())
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    @Test
    @DisplayName(
            "getActivities returns only cards within the requested month, sorted by actStartDate")
    void getActivities_filtersByMonthAndSorts() {
        Posting laterPosting = posting(101L, LocalDate.of(2026, 7, 20));
        Posting earlierPosting = posting(102L, LocalDate.of(2026, 7, 5));
        Posting outsideMonthPosting = posting(103L, LocalDate.of(2026, 8, 1));

        PostingParticipation laterParticipation = PostingParticipation.create(USER_ID, 101L);
        PostingParticipation earlierParticipation = PostingParticipation.create(USER_ID, 102L);
        PostingParticipation outsideParticipation = PostingParticipation.create(USER_ID, 103L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserIdAndStatusNotIn(
                            eq(USER_ID), anyCollection()))
                    .thenReturn(
                            List.of(
                                    laterParticipation,
                                    earlierParticipation,
                                    outsideParticipation));
            when(postingRepository.findAllById(List.of(101L, 102L, 103L)))
                    .thenReturn(List.of(laterPosting, earlierPosting, outsideMonthPosting));

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities)
                    .extracting(MyPageActivityResponse::postingId)
                    .containsExactly(102L, 101L);
        }
    }

    @Test
    @DisplayName("getActivities excludes COMPLETED/REVIEWED statuses via the repository query")
    void getActivities_requestsCalendarExcludedStatuses() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserIdAndStatusNotIn(
                            eq(USER_ID), anyCollection()))
                    .thenReturn(List.of());

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities).isEmpty();
            verify(postingParticipationRepository)
                    .findByUserIdAndStatusNotIn(
                            USER_ID,
                            Set.of(
                                    PostingParticipationStatus.COMPLETED,
                                    PostingParticipationStatus.REVIEWED));
        }
    }

    @Test
    @DisplayName(
            "getActivities includes activities on the first and last day of the month (inclusive"
                    + " boundaries)")
    void getActivities_includesFirstAndLastDayOfMonth() {
        Posting firstDayPosting = posting(201L, LocalDate.of(2026, 7, 1));
        Posting lastDayPosting = posting(202L, LocalDate.of(2026, 7, 31));
        PostingParticipation firstDayParticipation = PostingParticipation.create(USER_ID, 201L);
        PostingParticipation lastDayParticipation = PostingParticipation.create(USER_ID, 202L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserIdAndStatusNotIn(
                            eq(USER_ID), anyCollection()))
                    .thenReturn(List.of(firstDayParticipation, lastDayParticipation));
            when(postingRepository.findAllById(List.of(201L, 202L)))
                    .thenReturn(List.of(firstDayPosting, lastDayPosting));

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities)
                    .extracting(MyPageActivityResponse::postingId)
                    .containsExactlyInAnyOrder(201L, 202L);
        }
    }

    @Test
    @DisplayName(
            "getActivities includes a multi-day activity in every month it spans, and excludes it"
                    + " from months outside that range")
    void getActivities_includesMultiDayActivityInEveryOverlappingMonth() {
        Posting multiDayPosting =
                posting(401L, LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 2));
        PostingParticipation participation = PostingParticipation.create(USER_ID, 401L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserIdAndStatusNotIn(
                            eq(USER_ID), anyCollection()))
                    .thenReturn(List.of(participation));
            when(postingRepository.findAllById(List.of(401L))).thenReturn(List.of(multiDayPosting));

            List<MyPageActivityResponse> julyActivities =
                    myPageService.getActivities(YearMonth.of(2026, 7));
            List<MyPageActivityResponse> augustActivities =
                    myPageService.getActivities(YearMonth.of(2026, 8));
            List<MyPageActivityResponse> septemberActivities =
                    myPageService.getActivities(YearMonth.of(2026, 9));

            assertThat(julyActivities)
                    .extracting(MyPageActivityResponse::postingId)
                    .containsExactly(401L);
            assertThat(augustActivities)
                    .extracting(MyPageActivityResponse::postingId)
                    .containsExactly(401L);
            assertThat(septemberActivities).isEmpty();
        }
    }

    @Test
    @DisplayName("getActivities excludes a participation whose posting has no actStartDate yet")
    void getActivities_excludesPostingWithNullActStartDate() {
        Posting unscheduledPosting = posting(301L, null);
        PostingParticipation participation = PostingParticipation.create(USER_ID, 301L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserIdAndStatusNotIn(
                            eq(USER_ID), anyCollection()))
                    .thenReturn(List.of(participation));
            when(postingRepository.findAllById(List.of(301L)))
                    .thenReturn(List.of(unscheduledPosting));

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities).isEmpty();
        }
    }

    @Test
    @DisplayName("getActivitySummary counts completed participations per category and totals them")
    void getActivitySummary_groupsCompletedParticipationsByCategory() {
        Posting environmentPosting = posting(501L, LocalDate.of(2026, 7, 1));
        Posting welfarePosting =
                Posting.builder()
                        .title("복지 공고")
                        .status(PostingStatus.RECRUITING)
                        .activityDate(LocalDate.of(2026, 7, 2))
                        .actStartDate(LocalDate.of(2026, 7, 2))
                        .category(PostingCategory.WELFARE)
                        .build();
        ReflectionTestUtils.setField(welfarePosting, "id", 502L);

        PostingParticipation completed1 = completedParticipation(501L);
        PostingParticipation completed2 = completedParticipation(502L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserIdAndStatusIn(
                            eq(USER_ID), anyCollection()))
                    .thenReturn(List.of(completed1, completed2));
            when(postingRepository.findAllById(List.of(501L, 502L)))
                    .thenReturn(List.of(environmentPosting, welfarePosting));

            MyPageActivitySummaryResponse response = myPageService.getActivitySummary();

            assertThat(response.totalCompletedCount()).isEqualTo(2);
            assertThat(response.categoryBlocks())
                    .filteredOn(block -> block.category() == PostingCategory.ENVIRONMENT)
                    .extracting(MyPageActivitySummaryResponse.CategoryBlock::count)
                    .containsExactly(1L);
            assertThat(response.categoryBlocks())
                    .filteredOn(block -> block.category() == PostingCategory.CULTURE)
                    .extracting(MyPageActivitySummaryResponse.CategoryBlock::count)
                    .containsExactly(0L);
        }
    }

    @Test
    @DisplayName("getActivityRecords filters by category and sorts by actStartDate descending")
    void getActivityRecords_filtersByCategoryAndSortsDescending() {
        Posting olderEnvironment = posting(601L, LocalDate.of(2026, 6, 1));
        Posting newerEnvironment = posting(602L, LocalDate.of(2026, 7, 1));
        Posting welfarePosting =
                Posting.builder()
                        .title("복지 공고")
                        .status(PostingStatus.RECRUITING)
                        .activityDate(LocalDate.of(2026, 7, 10))
                        .actStartDate(LocalDate.of(2026, 7, 10))
                        .category(PostingCategory.WELFARE)
                        .build();
        ReflectionTestUtils.setField(welfarePosting, "id", 603L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserIdAndStatusIn(
                            eq(USER_ID), anyCollection()))
                    .thenReturn(
                            List.of(
                                    completedParticipation(601L),
                                    completedParticipation(602L),
                                    completedParticipation(603L)));
            when(postingRepository.findAllById(List.of(601L, 602L, 603L)))
                    .thenReturn(List.of(olderEnvironment, newerEnvironment, welfarePosting));

            List<MyPageActivityRecordResponse> records =
                    myPageService.getActivityRecords(PostingCategory.ENVIRONMENT);

            assertThat(records)
                    .extracting(MyPageActivityRecordResponse::postingId)
                    .containsExactly(602L, 601L);
        }
    }

    @Test
    @DisplayName("getBadges returns earned/total counts and achievedAt only for earned badges")
    void getBadges_returnsProgressAndAchievedAtForEarnedBadgesOnly() {
        Badge firstBadge = badge(1L, BadgeCode.FIRST_VOLUNTEER_COMPLETE);
        Badge secondBadge = badge(2L, BadgeCode.VOLUNTEER_5_COMPLETE);
        UserBadge earned = UserBadge.create(USER_ID, 1L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(badgeRepository.findAllByOrderByDisplayOrderAsc())
                    .thenReturn(List.of(firstBadge, secondBadge));
            when(userBadgeRepository.findByUserId(USER_ID)).thenReturn(List.of(earned));

            MyPageBadgeSummaryResponse response = myPageService.getBadges();

            assertThat(response.earnedCount()).isEqualTo(1);
            assertThat(response.totalCount()).isEqualTo(2);
            assertThat(response.progressRate()).isEqualTo(0.5);
            assertThat(response.badges())
                    .filteredOn(card -> card.badgeId().equals(1L))
                    .extracting(card -> card.achievedAt() != null)
                    .containsExactly(true);
            assertThat(response.badges())
                    .filteredOn(card -> card.badgeId().equals(2L))
                    .extracting(card -> card.achievedAt() != null)
                    .containsExactly(false);
        }
    }

    @Test
    @DisplayName("getBadges returns zero counts when no badges are seeded yet")
    void getBadges_returnsZeroCounts_whenBadgeTableEmpty() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(badgeRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of());
            when(userBadgeRepository.findByUserId(USER_ID)).thenReturn(List.of());

            MyPageBadgeSummaryResponse response = myPageService.getBadges();

            assertThat(response.earnedCount()).isZero();
            assertThat(response.totalCount()).isZero();
            assertThat(response.progressRate()).isZero();
            assertThat(response.badges()).isEmpty();
        }
    }

    private PostingParticipation completedParticipation(Long postingId) {
        PostingParticipation participation = PostingParticipation.create(USER_ID, postingId);
        participation.complete();
        return participation;
    }

    /** Badge는 Flyway 시드 데이터로만 채워지고 앱 코드에서 생성하지 않아 public 팩토리가 없다 - 테스트 전용으로 리플렉션 생성한다. */
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
        ReflectionTestUtils.setField(createdBadge, "name", code.name());
        ReflectionTestUtils.setField(createdBadge, "description", "설명");
        ReflectionTestUtils.setField(createdBadge, "targetDescription", "목표");
        ReflectionTestUtils.setField(createdBadge, "displayOrder", id.intValue());
        return createdBadge;
    }

    private User user() {
        Region activityRegion = Region.create("강남구", 2, "11680", null);
        User createdUser =
                User.create(
                        "홍길동",
                        LocalDate.of(2000, 1, 1),
                        Gender.MALE,
                        "01012345678",
                        "test@example.com",
                        "encoded-password",
                        "길동",
                        "기존 소개글",
                        true,
                        true,
                        false,
                        activityRegion,
                        List.of(PostingCategory.WELFARE));
        ReflectionTestUtils.setField(createdUser, "id", USER_ID);
        return createdUser;
    }

    private Posting posting(Long id, LocalDate actStartDate) {
        return posting(id, actStartDate, null);
    }

    private Posting posting(Long id, LocalDate actStartDate, LocalDate actEndDate) {
        Posting createdPosting =
                Posting.builder()
                        .title("테스트 공고 " + id)
                        .status(PostingStatus.RECRUITING)
                        .activityDate(actStartDate)
                        .actStartDate(actStartDate)
                        .actEndDate(actEndDate)
                        .category(PostingCategory.ENVIRONMENT)
                        .build();
        ReflectionTestUtils.setField(createdPosting, "id", id);
        return createdPosting;
    }
}
