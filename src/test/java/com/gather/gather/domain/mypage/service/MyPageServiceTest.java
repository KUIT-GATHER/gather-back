package com.gather.gather.domain.mypage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.entity.MeetingMember;
import com.gather.gather.domain.meeting.enums.MeetingMemberStatus;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.meeting.repository.MeetingMemberRepository;
import com.gather.gather.domain.mypage.dto.MyPageActivityRecordResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivityResponse;
import com.gather.gather.domain.mypage.dto.MyPageActivitySummaryResponse;
import com.gather.gather.domain.mypage.dto.MyPageHomeResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.posting.service.RegionNameResolver;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.user.service.ProfileImageUrlResolver;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    private static final Long USER_ID = 1L;
    private static final Set<PostingParticipationStatus> COMPLETED_STATUSES =
            Set.of(PostingParticipationStatus.COMPLETED, PostingParticipationStatus.REVIEWED);

    @Mock private UserRepository userRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private MeetingBookmarkRepository meetingBookmarkRepository;
    @Mock private PostingParticipationRepository postingParticipationRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private RegionNameResolver regionNameResolver;
    @Mock private ProfileImageUrlResolver profileImageUrlResolver;

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
                        meetingMemberRepository,
                        regionNameResolver,
                        profileImageUrlResolver);
        lenient()
                .when(
                        meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                                USER_ID, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED))
                .thenReturn(List.of());
        lenient()
                .when(meetingMemberRepository.findApprovedForCalendar(eq(USER_ID), any(), any()))
                .thenReturn(List.of());
        lenient().when(regionNameResolver.resolve(any(Collection.class))).thenReturn(Map.of());
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
            when(postingParticipationRepository.findByUserId(USER_ID))
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
    @DisplayName(
            "getActivities includes COMPLETED/REVIEWED participations so the 봉사 완료 tag can be"
                    + " shown, using the unfiltered repository query")
    void getActivities_includesCompletedAndReviewedParticipations() {
        Posting completedPosting = posting(701L, LocalDate.of(2026, 7, 10));
        Posting reviewedPosting = posting(702L, LocalDate.of(2026, 7, 11));
        PostingParticipation completedParticipation = PostingParticipation.create(USER_ID, 701L);
        ReflectionTestUtils.setField(
                completedParticipation, "status", PostingParticipationStatus.COMPLETED);
        PostingParticipation reviewedParticipation = PostingParticipation.create(USER_ID, 702L);
        ReflectionTestUtils.setField(
                reviewedParticipation, "status", PostingParticipationStatus.REVIEWED);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(completedParticipation, reviewedParticipation));
            when(postingRepository.findAllById(List.of(701L, 702L)))
                    .thenReturn(List.of(completedPosting, reviewedPosting));

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities)
                    .extracting(MyPageActivityResponse::postingId)
                    .containsExactlyInAnyOrder(701L, 702L);
            assertThat(activities)
                    .extracting(MyPageActivityResponse::status)
                    .containsExactlyInAnyOrder("COMPLETED", "REVIEWED");
            verify(postingParticipationRepository).findByUserId(USER_ID);
        }
    }

    @Test
    @DisplayName(
            "getActivities includes approved meeting memberships as MEETING cards alongside"
                    + " VOLUNTEER cards, merged and sorted by actStartDate")
    void getActivities_mergesMeetingActivitiesWithVolunteerActivities() {
        Posting volunteerPosting = posting(801L, LocalDate.of(2026, 7, 20));
        PostingParticipation participation = PostingParticipation.create(USER_ID, 801L);
        MeetingMember meetingMember =
                approvedCalendarMeetingMember(
                        3L,
                        LocalDate.of(2026, 7, 5).atStartOfDay(),
                        LocalDate.of(2026, 7, 6).atStartOfDay());

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(participation));
            when(postingRepository.findAllById(List.of(801L)))
                    .thenReturn(List.of(volunteerPosting));
            when(meetingMemberRepository.findApprovedForCalendar(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(meetingMember));

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities)
                    .extracting(MyPageActivityResponse::activityType)
                    .containsExactly(
                            MyPageActivityResponse.ActivityType.MEETING,
                            MyPageActivityResponse.ActivityType.VOLUNTEER);
            assertThat(activities.get(0).meetingId()).isEqualTo(3L);
            assertThat(activities.get(0).postingId()).isNull();
            assertThat(activities.get(0).volunteerPostingId()).isNull();
            assertThat(activities.get(0).meetingStatus()).isEqualTo("RECRUITING");
            assertThat(activities.get(0).postingParticipationStatus()).isNull();
            assertThat(activities.get(1).meetingId()).isNull();
            assertThat(activities.get(1).postingId()).isEqualTo(801L);
            verify(postingParticipationRepository, never())
                    .findAllByUserIdAndPostingIdIn(any(), any());
        }
    }

    @Test
    @DisplayName(
            "getActivities returns the meeting's region name as regionName for a MEETING card, and"
                    + " null regionName for a VOLUNTEER card")
    void getActivities_returnsRegionNameForMeetingCard_andNullForVolunteerCard() {
        Posting volunteerPosting = posting(801L, LocalDate.of(2026, 7, 20));
        PostingParticipation participation = PostingParticipation.create(USER_ID, 801L);
        MeetingMember meetingMember =
                approvedCalendarMeetingMember(
                        3L,
                        LocalDate.of(2026, 7, 5).atStartOfDay(),
                        LocalDate.of(2026, 7, 6).atStartOfDay());

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(participation));
            when(postingRepository.findAllById(List.of(801L)))
                    .thenReturn(List.of(volunteerPosting));
            when(meetingMemberRepository.findApprovedForCalendar(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(meetingMember));
            when(regionNameResolver.resolve(List.of(1L))).thenReturn(Map.of(1L, "강남구"));

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities.get(0).activityType())
                    .isEqualTo(MyPageActivityResponse.ActivityType.MEETING);
            assertThat(activities.get(0).regionName()).isEqualTo("강남구");
            assertThat(activities.get(1).activityType())
                    .isEqualTo(MyPageActivityResponse.ActivityType.VOLUNTEER);
            assertThat(activities.get(1).regionName()).isNull();
        }
    }

    @Test
    @DisplayName(
            "getActivities returns the linked posting's PostingParticipation status as"
                    + " postingParticipationStatus for a posting-based MEETING card")
    void getActivities_returnsPostingParticipationStatus_forPostingBasedMeetingWithParticipation() {
        MeetingMember meetingMember =
                approvedCalendarMeetingMember(
                        3L,
                        10L,
                        LocalDate.of(2026, 7, 5).atStartOfDay(),
                        LocalDate.of(2026, 7, 6).atStartOfDay());
        PostingParticipation linkedParticipation = PostingParticipation.create(USER_ID, 10L);
        ReflectionTestUtils.setField(
                linkedParticipation, "status", PostingParticipationStatus.APPLIED);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserId(USER_ID)).thenReturn(List.of());
            when(meetingMemberRepository.findApprovedForCalendar(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(meetingMember));
            when(postingParticipationRepository.findAllByUserIdAndPostingIdIn(
                            USER_ID, List.of(10L)))
                    .thenReturn(List.of(linkedParticipation));

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities.get(0).volunteerPostingId()).isEqualTo(10L);
            assertThat(activities.get(0).meetingStatus()).isEqualTo("RECRUITING");
            assertThat(activities.get(0).postingParticipationStatus()).isEqualTo("APPLIED");
        }
    }

    @Test
    @DisplayName(
            "getActivities returns null postingParticipationStatus when the posting-based MEETING's"
                    + " linked posting has no participation record for the user")
    void getActivities_returnsNullPostingParticipationStatus_whenLinkedPostingHasNoParticipation() {
        MeetingMember meetingMember =
                approvedCalendarMeetingMember(
                        3L,
                        10L,
                        LocalDate.of(2026, 7, 5).atStartOfDay(),
                        LocalDate.of(2026, 7, 6).atStartOfDay());

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findByUserId(USER_ID)).thenReturn(List.of());
            when(meetingMemberRepository.findApprovedForCalendar(eq(USER_ID), any(), any()))
                    .thenReturn(List.of(meetingMember));
            when(postingParticipationRepository.findAllByUserIdAndPostingIdIn(
                            USER_ID, List.of(10L)))
                    .thenReturn(List.of());

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities.get(0).volunteerPostingId()).isEqualTo(10L);
            assertThat(activities.get(0).postingParticipationStatus()).isNull();
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
            when(postingParticipationRepository.findByUserId(USER_ID))
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
            when(postingParticipationRepository.findByUserId(USER_ID))
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
            when(postingParticipationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(participation));
            when(postingRepository.findAllById(List.of(301L)))
                    .thenReturn(List.of(unscheduledPosting));

            List<MyPageActivityResponse> activities =
                    myPageService.getActivities(YearMonth.of(2026, 7));

            assertThat(activities).isEmpty();
        }
    }

    @Test
    @DisplayName(
            "getActivitySummary counts only COMPLETED participations and fills every category"
                    + " (0 for categories with no completion)")
    void getActivitySummary_countsCompletedParticipationsByCategory() {
        Posting environmentPosting = posting(101L, LocalDate.of(2026, 7, 20));
        Posting educationPosting =
                posting(102L, LocalDate.of(2026, 7, 5), PostingCategory.EDUCATION);

        PostingParticipation first = PostingParticipation.create(USER_ID, 101L);
        PostingParticipation second = PostingParticipation.create(USER_ID, 102L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(first, second));
            when(postingRepository.findAllById(List.of(101L, 102L)))
                    .thenReturn(List.of(environmentPosting, educationPosting));

            MyPageActivitySummaryResponse summary = myPageService.getActivitySummary();

            assertThat(summary.totalCompletedCount()).isEqualTo(2);
            assertThat(summary.categoryBlocks())
                    .hasSize(PostingCategory.values().length)
                    .filteredOn(block -> block.category() == PostingCategory.ENVIRONMENT)
                    .extracting(MyPageActivitySummaryResponse.CategoryBlock::count)
                    .containsExactly(1L);
            assertThat(summary.categoryBlocks())
                    .filteredOn(block -> block.category() == PostingCategory.CULTURE)
                    .extracting(MyPageActivitySummaryResponse.CategoryBlock::count)
                    .containsExactly(0L);
        }
    }

    @Test
    @DisplayName(
            "getActivityRecords returns completed cards sorted by latest actStartDate first,"
                    + " including recognizedMinutes")
    void getActivityRecords_returnsCompletedCardsSortedByLatestFirst() {
        Posting earlierPosting = posting(201L, LocalDate.of(2026, 7, 1));
        Posting laterPosting = posting(202L, LocalDate.of(2026, 7, 20));

        PostingParticipation earlierParticipation = PostingParticipation.create(USER_ID, 201L);
        earlierParticipation.submitRecognizedMinutes(90);
        PostingParticipation laterParticipation = PostingParticipation.create(USER_ID, 202L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(earlierParticipation, laterParticipation));
            when(postingRepository.findAllById(List.of(201L, 202L)))
                    .thenReturn(List.of(earlierPosting, laterPosting));

            List<MyPageActivityRecordResponse> records =
                    myPageService.getActivityRecords(null, defaultPageable()).content();

            assertThat(records)
                    .extracting(MyPageActivityRecordResponse::postingId)
                    .containsExactly(202L, 201L);
            assertThat(records.get(1).recognizedMinutes()).isEqualTo(90);
        }
    }

    @Test
    @DisplayName(
            "getActivityRecords does not throw and sorts a null actStartDate last when a"
                    + " completed posting has no actStartDate")
    void getActivityRecords_sortsNullActStartDateLast() {
        Posting datedPosting = posting(203L, LocalDate.of(2026, 7, 10));
        Posting undatedPosting = postingWithNullActStartDate(204L);

        PostingParticipation datedParticipation = PostingParticipation.create(USER_ID, 203L);
        PostingParticipation undatedParticipation = PostingParticipation.create(USER_ID, 204L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(undatedParticipation, datedParticipation));
            when(postingRepository.findAllById(List.of(204L, 203L)))
                    .thenReturn(List.of(undatedPosting, datedPosting));

            List<MyPageActivityRecordResponse> records =
                    myPageService.getActivityRecords(null, defaultPageable()).content();

            assertThat(records)
                    .extracting(MyPageActivityRecordResponse::postingId)
                    .containsExactly(203L, 204L);
        }
    }

    @Test
    @DisplayName("getActivityRecords filters to only the requested category when provided")
    void getActivityRecords_filtersByCategory() {
        Posting environmentPosting = posting(301L, LocalDate.of(2026, 7, 10));
        Posting educationPosting =
                posting(302L, LocalDate.of(2026, 7, 11), PostingCategory.EDUCATION);

        PostingParticipation environmentParticipation = PostingParticipation.create(USER_ID, 301L);
        PostingParticipation educationParticipation = PostingParticipation.create(USER_ID, 302L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(environmentParticipation, educationParticipation));
            when(postingRepository.findAllById(List.of(301L, 302L)))
                    .thenReturn(List.of(environmentPosting, educationPosting));

            List<MyPageActivityRecordResponse> records =
                    myPageService
                            .getActivityRecords(PostingCategory.EDUCATION, defaultPageable())
                            .content();

            assertThat(records)
                    .extracting(MyPageActivityRecordResponse::postingId)
                    .containsExactly(302L);
        }
    }

    @Test
    @DisplayName(
            "getActivityRecords returns an empty list when there are no completed participations")
    void getActivityRecords_returnsEmpty_whenNoCompletedParticipations() {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of());

            List<MyPageActivityRecordResponse> records =
                    myPageService.getActivityRecords(null, defaultPageable()).content();

            assertThat(records).isEmpty();
        }
    }

    @Test
    @DisplayName(
            "getActivitySummary adds completed meeting volunteering into totalCompletedCount but"
                    + " not into categoryBlocks (M-3)")
    void getActivitySummary_includesMeetingCompletionsInTotalOnly() {
        Posting environmentPosting = posting(101L, LocalDate.of(2026, 7, 20));
        PostingParticipation participation = PostingParticipation.create(USER_ID, 101L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(participation));
            when(postingRepository.findAllById(List.of(101L)))
                    .thenReturn(List.of(environmentPosting));
            when(meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                            USER_ID, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED))
                    .thenReturn(List.of(approvedMeetingMember(), approvedMeetingMember()));

            MyPageActivitySummaryResponse summary = myPageService.getActivitySummary();

            assertThat(summary.totalCompletedCount()).isEqualTo(3);
            assertThat(
                            summary.categoryBlocks().stream()
                                    .mapToLong(MyPageActivitySummaryResponse.CategoryBlock::count)
                                    .sum())
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName(
            "getActivitySummary keeps totalCompletedCount equal to the sum of categoryBlocks for"
                    + " the posting portion when a posting lookup fails (L-2)")
    void getActivitySummary_totalMatchesBlockSum_whenPostingLookupFails() {
        PostingParticipation orphanParticipation = PostingParticipation.create(USER_ID, 999L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(orphanParticipation));
            when(postingRepository.findAllById(List.of(999L))).thenReturn(List.of());

            MyPageActivitySummaryResponse summary = myPageService.getActivitySummary();

            long blockSum =
                    summary.categoryBlocks().stream()
                            .mapToLong(MyPageActivitySummaryResponse.CategoryBlock::count)
                            .sum();
            assertThat(summary.totalCompletedCount()).isEqualTo(blockSum);
        }
    }

    @Test
    @DisplayName(
            "getActivityRecords breaks ties on the same actStartDate using participation id"
                    + " descending, for a stable order (L-6)")
    void getActivityRecords_breaksTiesOnSameDateByParticipationId() {
        Posting samedayPostingA = posting(501L, LocalDate.of(2026, 7, 10));
        Posting samedayPostingB = posting(502L, LocalDate.of(2026, 7, 10));
        PostingParticipation participationA = PostingParticipation.create(USER_ID, 501L);
        PostingParticipation participationB = PostingParticipation.create(USER_ID, 502L);
        ReflectionTestUtils.setField(participationA, "id", 11L);
        ReflectionTestUtils.setField(participationB, "id", 12L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(participationA, participationB));
            when(postingRepository.findAllById(List.of(501L, 502L)))
                    .thenReturn(List.of(samedayPostingA, samedayPostingB));

            List<MyPageActivityRecordResponse> records =
                    myPageService.getActivityRecords(null, defaultPageable()).content();

            assertThat(records)
                    .extracting(MyPageActivityRecordResponse::postingId)
                    .containsExactly(502L, 501L);
        }
    }

    @Test
    @DisplayName("getActivityRecords paginates the result according to the given Pageable (M-8)")
    void getActivityRecords_paginatesResults() {
        Posting first = posting(601L, LocalDate.of(2026, 7, 1));
        Posting second = posting(602L, LocalDate.of(2026, 7, 2));
        Posting third = posting(603L, LocalDate.of(2026, 7, 3));
        PostingParticipation p1 = PostingParticipation.create(USER_ID, 601L);
        PostingParticipation p2 = PostingParticipation.create(USER_ID, 602L);
        PostingParticipation p3 = PostingParticipation.create(USER_ID, 603L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(p1, p2, p3));
            when(postingRepository.findAllById(List.of(601L, 602L, 603L)))
                    .thenReturn(List.of(first, second, third));

            PageResponse<MyPageActivityRecordResponse> page =
                    myPageService.getActivityRecords(null, PageRequest.of(0, 2));

            assertThat(page.content()).hasSize(2);
            assertThat(page.totalElements()).isEqualTo(3);
            assertThat(page.totalPages()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName(
            "getActivityRecords returns an empty page instead of throwing when the requested page"
                    + " offset overflows int range")
    void getActivityRecords_returnsEmptyPage_whenOffsetOverflowsInt() {
        Posting first = posting(601L, LocalDate.of(2026, 7, 1));
        PostingParticipation p1 = PostingParticipation.create(USER_ID, 601L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(p1));
            when(postingRepository.findAllById(List.of(601L))).thenReturn(List.of(first));

            PageResponse<MyPageActivityRecordResponse> page =
                    myPageService.getActivityRecords(null, PageRequest.of(2_000_000, 2000));

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName(
            "getActivitySummary sums totalRecognizedMinutes and counts"
                    + " timeCertifiableCompletedCount across posting participations and meeting"
                    + " memberships, ignoring cards with no recognizedMinutes submitted")
    void getActivitySummary_aggregatesRecognizedMinutesAndTimeCertifiableCount() {
        Posting certifiedPosting = posting(101L, LocalDate.of(2026, 7, 20));
        PostingParticipation certifiedParticipation = PostingParticipation.create(USER_ID, 101L);
        certifiedParticipation.submitRecognizedMinutes(90);
        Posting uncertifiedPosting =
                posting(102L, LocalDate.of(2026, 7, 21), PostingCategory.EDUCATION);
        PostingParticipation uncertifiedParticipation = PostingParticipation.create(USER_ID, 102L);

        MeetingMember certifiedMember = approvedMeetingMember();
        certifiedMember.submitRecognizedMinutes(60);
        MeetingMember uncertifiedMember = approvedMeetingMember();

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(certifiedParticipation, uncertifiedParticipation));
            when(postingRepository.findAllById(List.of(101L, 102L)))
                    .thenReturn(List.of(certifiedPosting, uncertifiedPosting));
            when(meetingMemberRepository.findAllByUserIdAndStatusAndMeetingStatus(
                            USER_ID, MeetingMemberStatus.APPROVED, MeetingStatus.COMPLETED))
                    .thenReturn(List.of(certifiedMember, uncertifiedMember));

            MyPageActivitySummaryResponse summary = myPageService.getActivitySummary();

            assertThat(summary.totalRecognizedMinutes()).isEqualTo(150);
            assertThat(summary.timeCertifiableCompletedCount()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName(
            "getActivityRecords sets timeCertifiable=true only for cards with recognizedMinutes"
                    + " already submitted")
    void getActivityRecords_setsTimeCertifiableBasedOnRecognizedMinutes() {
        Posting certifiedPosting = posting(201L, LocalDate.of(2026, 7, 10));
        Posting uncertifiedPosting = posting(202L, LocalDate.of(2026, 7, 11));
        PostingParticipation certifiedParticipation = PostingParticipation.create(USER_ID, 201L);
        certifiedParticipation.submitRecognizedMinutes(120);
        PostingParticipation uncertifiedParticipation = PostingParticipation.create(USER_ID, 202L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(USER_ID);
            when(postingParticipationRepository.findAllByUserIdAndStatusIn(
                            USER_ID, COMPLETED_STATUSES))
                    .thenReturn(List.of(certifiedParticipation, uncertifiedParticipation));
            when(postingRepository.findAllById(List.of(201L, 202L)))
                    .thenReturn(List.of(certifiedPosting, uncertifiedPosting));

            List<MyPageActivityRecordResponse> records =
                    myPageService.getActivityRecords(null, defaultPageable()).content();

            assertThat(records)
                    .filteredOn(record -> record.postingId().equals(201L))
                    .extracting(MyPageActivityRecordResponse::timeCertifiable)
                    .containsExactly(true);
            assertThat(records)
                    .filteredOn(record -> record.postingId().equals(202L))
                    .extracting(MyPageActivityRecordResponse::timeCertifiable)
                    .containsExactly(false);
        }
    }

    private Pageable defaultPageable() {
        return PageRequest.of(0, 20);
    }

    private MeetingMember approvedCalendarMeetingMember(
            Long meetingId, LocalDateTime activityStartAt, LocalDateTime activityEndAt) {
        return approvedCalendarMeetingMember(meetingId, null, activityStartAt, activityEndAt);
    }

    private MeetingMember approvedCalendarMeetingMember(
            Long meetingId,
            Long volunteerPostingId,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt) {
        Meeting meeting =
                Meeting.create(
                        "테스트 모임",
                        "설명",
                        5,
                        activityStartAt.minusDays(10),
                        null,
                        Set.of(PostingCategory.ENVIRONMENT),
                        1L,
                        user(),
                        null,
                        volunteerPostingId,
                        activityStartAt,
                        activityEndAt);
        ReflectionTestUtils.setField(meeting, "id", meetingId);
        MeetingMember member = MeetingMember.createHost(user(), meeting);
        ReflectionTestUtils.setField(member, "status", MeetingMemberStatus.APPROVED);
        return member;
    }

    private MeetingMember approvedMeetingMember() {
        Meeting meeting =
                Meeting.create(
                        "테스트 모임",
                        "설명",
                        5,
                        LocalDate.of(2026, 6, 1).atStartOfDay(),
                        null,
                        Set.of(PostingCategory.ENVIRONMENT),
                        1L,
                        user(),
                        null,
                        null,
                        LocalDate.of(2026, 6, 2).atStartOfDay(),
                        LocalDate.of(2026, 6, 3).atStartOfDay());
        ReflectionTestUtils.setField(meeting, "status", MeetingStatus.COMPLETED);
        MeetingMember member = MeetingMember.createHost(user(), meeting);
        ReflectionTestUtils.setField(member, "status", MeetingMemberStatus.APPROVED);
        return member;
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
        return posting(id, actStartDate, (LocalDate) null);
    }

    /** activityDate만 있고 actStartDate/actEndDate는 없는 공고 — 실제로 존재할 수 있는 데이터 형태를 재현한다. */
    private Posting postingWithNullActStartDate(Long id) {
        Posting createdPosting =
                Posting.builder()
                        .title("테스트 공고 " + id)
                        .status(PostingStatus.RECRUITING)
                        .activityDate(LocalDate.of(2026, 7, 1))
                        .category(PostingCategory.ENVIRONMENT)
                        .build();
        ReflectionTestUtils.setField(createdPosting, "id", id);
        return createdPosting;
    }

    private Posting posting(Long id, LocalDate actStartDate, LocalDate actEndDate) {
        return posting(id, actStartDate, actEndDate, PostingCategory.ENVIRONMENT);
    }

    private Posting posting(Long id, LocalDate actStartDate, PostingCategory category) {
        return posting(id, actStartDate, null, category);
    }

    private Posting posting(
            Long id, LocalDate actStartDate, LocalDate actEndDate, PostingCategory category) {
        Posting createdPosting =
                Posting.builder()
                        .title("테스트 공고 " + id)
                        .status(PostingStatus.RECRUITING)
                        .activityDate(actStartDate)
                        .actStartDate(actStartDate)
                        .actEndDate(actEndDate)
                        .category(category)
                        .build();
        ReflectionTestUtils.setField(createdPosting, "id", id);
        return createdPosting;
    }
}
