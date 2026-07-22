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
import com.gather.gather.domain.meeting.repository.MeetingBookmarkRepository;
import com.gather.gather.domain.mypage.dto.MyPageActivityResponse;
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
                        profileImageUrlResolver);
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

            myPageService.getActivities(YearMonth.of(2026, 7));

            verify(postingParticipationRepository)
                    .findByUserIdAndStatusNotIn(
                            USER_ID,
                            Set.of(
                                    PostingParticipationStatus.COMPLETED,
                                    PostingParticipationStatus.REVIEWED));
        }
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
        Posting createdPosting =
                Posting.builder()
                        .title("테스트 공고 " + id)
                        .status(PostingStatus.RECRUITING)
                        .activityDate(actStartDate)
                        .actStartDate(actStartDate)
                        .category(PostingCategory.ENVIRONMENT)
                        .build();
        ReflectionTestUtils.setField(createdPosting, "id", id);
        return createdPosting;
    }
}
