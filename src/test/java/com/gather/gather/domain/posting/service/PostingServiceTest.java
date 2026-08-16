package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import com.gather.gather.domain.meeting.service.MeetingImageUrlResolver;
import com.gather.gather.domain.posting.dto.PostingListItem;
import com.gather.gather.domain.posting.dto.PostingParticipationAction;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSourceType;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingLocation;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingLocationRepository;
import com.gather.gather.domain.posting.repository.PostingMapRow;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository.SearchResult;
import com.gather.gather.domain.posting.repository.UnifiedPostingRow;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostingServiceTest {

    @Mock private PostingRepository postingRepository;
    @Mock private PostingLocationRepository postingLocationRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private PostingSearchLogService postingSearchLogService;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private PostingParticipationRepository postingParticipationRepository;
    @Mock private UnifiedPostingQueryRepository unifiedPostingQueryRepository;
    @Mock private MeetingImageRepository meetingImageRepository;
    @Mock private MeetingImageUrlResolver meetingImageUrlResolver;
    @Mock private PostingApplicationUrlResolver postingApplicationUrlResolver;
    private PostingService postingService;

    @BeforeEach
    void setUp() {
        postingService =
                new PostingService(
                        postingRepository,
                        postingLocationRepository,
                        regionRepository,
                        postingSearchLogService,
                        new RegionNameResolver(regionRepository),
                        bookmarkRepository,
                        postingParticipationRepository,
                        unifiedPostingQueryRepository,
                        meetingImageRepository,
                        meetingImageUrlResolver,
                        new ObjectMapper(),
                        postingApplicationUrlResolver);
    }

    @Test
    @DisplayName("getPostings passes a null status through to the repository unchanged")
    void getPostings_passesNullStatusThrough_whenStatusNotProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(pageable, null, null, null, null, null, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, null, null, null, null, pageable);
        verify(regionRepository, never()).findIdsIncludingChildren(any());
        verify(regionRepository, never()).findIdsIncludingChildrenByGroupId(any());
    }

    @Test
    @DisplayName("getPostings passes an explicitly given status through unchanged")
    void getPostings_usesGivenStatus_whenProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(unifiedPostingQueryRepository.search(
                        eq(PostingStatus.CLOSED),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(
                pageable, null, null, PostingStatus.CLOSED, null, null, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(PostingStatus.CLOSED, null, null, null, null, null, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings resolves regionId to itself plus children before querying")
    void getPostings_resolvesRegionHierarchy_whenRegionIdProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(regionRepository.findIdsIncludingChildren(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        eq(List.of(1L, 2L, 3L)),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(pageable, 1L, null, null, null, null, null, null, null, null);

        verify(regionRepository).findIdsIncludingChildren(1L);
        verify(unifiedPostingQueryRepository)
                .search(null, List.of(1L, 2L, 3L), null, null, null, null, null, null, pageable);
    }

    @Test
    @DisplayName(
            "getPostings resolves regionGroupId to every sido/gungu in that group before"
                    + " querying")
    void getPostings_resolvesRegionGroupHierarchy_whenRegionGroupIdProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(regionRepository.findIdsIncludingChildrenByGroupId(7L))
                .thenReturn(List.of(10L, 11L, 12L, 13L));
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        eq(List.of(10L, 11L, 12L, 13L)),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(pageable, null, 7L, null, null, null, null, null, null, null);

        verify(regionRepository).findIdsIncludingChildrenByGroupId(7L);
        verify(regionRepository, never()).findIdsIncludingChildren(any());
        verify(unifiedPostingQueryRepository)
                .search(
                        null,
                        List.of(10L, 11L, 12L, 13L),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        pageable);
    }

    @Test
    @DisplayName("getPostings throws VALIDATION_ERROR when both regionId and regionGroupId given")
    void getPostings_throwsValidationError_whenBothRegionIdAndRegionGroupIdProvided() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(
                        () ->
                                postingService.getPostings(
                                        pageable, 1L, 7L, null, null, null, null, null, "환경", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(unifiedPostingQueryRepository, never())
                .search(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(postingSearchLogService, never()).log(any());
    }

    @Test
    @DisplayName("getPostings throws VALIDATION_ERROR when sort property is not whitelisted")
    void getPostings_throwsValidationError_whenSortPropertyUnknown() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("string"));

        assertThatThrownBy(
                        () ->
                                postingService.getPostings(
                                        pageable, null, null, null, null, null, null, null, "환경",
                                        null))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(postingSearchLogService, never()).log(any());
        verify(unifiedPostingQueryRepository, never())
                .search(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPostings allows sorting by a whitelisted property")
    void getPostings_allowsSort_whenPropertyKnown() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("activityStartAt").ascending());
        when(unifiedPostingQueryRepository.isSortable("activityStartAt")).thenReturn(true);
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(pageable, null, null, null, null, null, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, null, null, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings logs the keyword only after the search succeeds")
    void getPostings_logsKeyword_onlyAfterSearchSucceeds() {
        Pageable pageable = PageRequest.of(0, 20);
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("환경"),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(pageable, null, null, null, null, null, null, null, "환경", null);

        verify(postingSearchLogService).log("환경");
    }

    @Test
    @DisplayName("getPostings still returns results when search-log recording throws")
    void getPostings_returnsResults_whenSearchLoggingThrows() {
        Pageable pageable = PageRequest.of(0, 20);
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("환경"),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));
        doThrow(new RuntimeException("logging failed")).when(postingSearchLogService).log("환경");

        PageResponse<PostingListItem> result =
                postingService.getPostings(
                        pageable, null, null, null, null, null, null, null, "환경", null);

        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("getPostings passes the notice date range through to the repository")
    void getPostings_passesNoticeDateRange() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        eq(from),
                        eq(to),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(pageable, null, null, null, from, to, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, from, to, null, null, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings passes the activity date range through to the repository")
    void getPostings_passesActivityDateRange() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDate from = LocalDate.of(2026, 8, 20);
        LocalDate to = LocalDate.of(2026, 8, 25);
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(from),
                        eq(to),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(pageable, null, null, null, null, null, from, to, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, from, to, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings throws VALIDATION_ERROR when activityStartDate is after activityEndDate")
    void getPostings_throwsValidationError_whenActivityDateRangeInverted() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDate start = LocalDate.of(2026, 8, 25);
        LocalDate end = LocalDate.of(2026, 8, 20);

        assertThatThrownBy(
                        () ->
                                postingService.getPostings(
                                        pageable, null, null, null, null, null, start, end, null,
                                        null))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(unifiedPostingQueryRepository, never())
                .search(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPostings allows activityStartDate equal to activityEndDate")
    void getPostings_allowsActivityDateRange_whenStartEqualsEnd() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDate same = LocalDate.of(2026, 8, 20);
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(same),
                        eq(same),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(
                pageable, null, null, null, null, null, same, same, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, same, same, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings passes the category through to the repository")
    void getPostings_passesCategory_whenProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(PostingCategory.WELFARE),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        postingService.getPostings(
                pageable, null, null, null, null, null, null, null, null, PostingCategory.WELFARE);

        verify(unifiedPostingQueryRepository)
                .search(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        PostingCategory.WELFARE,
                        pageable);
    }

    @Test
    @DisplayName(
            "getPostings maps a POSTING-sourced row to a PostingListItem with a null thumbnail")
    void getPostings_mapsPostingSourcedRow_withNullThumbnail() {
        Pageable pageable = PageRequest.of(0, 20);
        UnifiedPostingRow row =
                new UnifiedPostingRow(
                        "POSTING",
                        1L,
                        null,
                        "동구 환경정화 봉사",
                        "울산 동구청",
                        2L,
                        "동구 일대",
                        LocalDateTime.of(2026, 7, 10, 9, 0),
                        LocalDateTime.of(2026, 7, 10, 18, 0),
                        LocalDateTime.of(2026, 7, 9, 23, 59),
                        5,
                        1,
                        "[\"ENVIRONMENT\"]",
                        "RECRUITING");
        when(unifiedPostingQueryRepository.search(
                        any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SearchResult(List.of(row), 1));
        when(regionRepository.findAllById(any())).thenReturn(List.of(regionWithId(2L, "동구")));

        PageResponse<PostingListItem> result =
                postingService.getPostings(
                        pageable, null, null, null, null, null, null, null, null, null);

        assertThat(result.content()).hasSize(1);
        PostingListItem item = result.content().get(0);
        assertThat(item.sourceType()).isEqualTo(PostingSourceType.POSTING);
        assertThat(item.regionName()).isEqualTo("동구");
        assertThat(item.thumbnailUrl()).isNull();
        assertThat(item.categories()).containsExactly(PostingCategory.ENVIRONMENT);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(meetingImageRepository, never()).findRepresentativeImagesByMeetingIds(any());
    }

    @Test
    @DisplayName("getPostings resolves a thumbnail for a MEETING_RECRUIT-sourced row")
    void getPostings_resolvesThumbnail_forMeetingRecruitSourcedRow() {
        Pageable pageable = PageRequest.of(0, 20);
        UnifiedPostingRow row =
                new UnifiedPostingRow(
                        "MEETING_RECRUIT",
                        100L,
                        9L,
                        "6월 정기 활동 팀원 모집",
                        "한강공원 플로깅",
                        2L,
                        "여의도동",
                        LocalDateTime.of(2026, 7, 10, 9, 0),
                        LocalDateTime.of(2026, 7, 10, 18, 0),
                        LocalDateTime.of(2026, 7, 9, 23, 59),
                        4,
                        2,
                        "[\"ENVIRONMENT\"]",
                        "RECRUITING");
        com.gather.gather.domain.meeting.entity.MeetingImage image =
                com.gather.gather.domain.meeting.entity.MeetingImage.create(
                        9L, "meetings/9/a.jpg", 0);
        when(unifiedPostingQueryRepository.search(
                        any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SearchResult(List.of(row), 1));
        when(regionRepository.findAllById(any())).thenReturn(List.of());
        when(meetingImageRepository.findRepresentativeImagesByMeetingIds(any()))
                .thenReturn(List.of(image));
        when(meetingImageUrlResolver.resolve("meetings/9/a.jpg"))
                .thenReturn("https://cdn.example.com/meetings/9/a.jpg");

        PageResponse<PostingListItem> result =
                postingService.getPostings(
                        pageable, null, null, null, null, null, null, null, null, null);

        PostingListItem item = result.content().get(0);
        assertThat(item.sourceType()).isEqualTo(PostingSourceType.MEETING_RECRUIT);
        assertThat(item.meetingId()).isEqualTo(9L);
        assertThat(item.thumbnailUrl()).isEqualTo("https://cdn.example.com/meetings/9/a.jpg");
    }

    @Test
    @DisplayName("getPostings returns empty PageResponse when no rows exist")
    void getPostings_returnsEmptyPageResponse_whenNoRowsExist() {
        Pageable pageable = PageRequest.of(0, 20);
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new SearchResult(List.of(), 0));

        PageResponse<PostingListItem> result =
                postingService.getPostings(
                        pageable, null, null, null, null, null, null, null, null, null);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("getPosting returns detail with locations when posting exists")
    void getPosting_returnsDetailWithLocations_whenExists() {
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, PostingCategory.ENVIRONMENT);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(regionWithId(2L, "동구")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(
                        List.of(locationWithId(2, "부산시 어딘가 2"), locationWithId(3, "부산시 어딘가 3")));

        PostingResponse response = postingService.getPosting(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.regionName()).isEqualTo("동구");
        assertThat(response.category()).isEqualTo(PostingCategory.ENVIRONMENT);
        assertThat(response.locations()).hasSize(3);
        assertThat(response.locations().get(0).locationSeq()).isEqualTo(1);
        assertThat(response.locations().get(1).locationSeq()).isEqualTo(2);
        assertThat(response.locations().get(2).locationSeq()).isEqualTo(3);
        assertThat(response.bookmarked()).isFalse();
        assertThat(response.participationStatus()).isNull();
        assertThat(response.participationAction()).isEqualTo(PostingParticipationAction.APPLY);
    }

    @Test
    @DisplayName("getPosting returns bookmarked true when the current user has bookmarked it")
    void getPosting_returnsBookmarkedTrue_whenCurrentUserHasBookmarked() {
        Long userId = 1L;
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, PostingCategory.ENVIRONMENT);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(regionWithId(2L, "동구")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(List.of());
        when(bookmarkRepository.existsByUserIdAndPostingId(userId, 1L)).thenReturn(true);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(userId);

            PostingResponse response = postingService.getPosting(1L);

            assertThat(response.bookmarked()).isTrue();
        }
    }

    @Test
    @DisplayName("getPosting returns bookmarked false when the current user has not bookmarked it")
    void getPosting_returnsBookmarkedFalse_whenCurrentUserHasNotBookmarked() {
        Long userId = 1L;
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, PostingCategory.ENVIRONMENT);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(regionWithId(2L, "동구")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(List.of());
        when(bookmarkRepository.existsByUserIdAndPostingId(userId, 1L)).thenReturn(false);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(userId);

            PostingResponse response = postingService.getPosting(1L);

            assertThat(response.bookmarked()).isFalse();
        }
    }

    @Test
    @DisplayName("getPosting returns bookmarked false without querying bookmarks when anonymous")
    void getPosting_returnsBookmarkedFalse_whenAnonymous() {
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, PostingCategory.ENVIRONMENT);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(regionWithId(2L, "동구")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(List.of());

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(null);

            PostingResponse response = postingService.getPosting(1L);

            assertThat(response.bookmarked()).isFalse();
        }
        verify(bookmarkRepository, never()).existsByUserIdAndPostingId(any(), any());
    }

    @Test
    @DisplayName("getPosting does not query participation and returns APPLY action when anonymous")
    void getPosting_doesNotQueryParticipation_whenAnonymous() {
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, PostingCategory.ENVIRONMENT);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(regionWithId(2L, "동구")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(List.of());

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(null);

            PostingResponse response = postingService.getPosting(1L);

            assertThat(response.participationStatus()).isNull();
            assertThat(response.participationAction()).isEqualTo(PostingParticipationAction.APPLY);
        }
        verify(postingParticipationRepository, never()).findByUserIdAndPostingId(any(), any());
    }

    @Test
    @DisplayName(
            "getPosting returns null status and APPLY action when the user has not participated")
    void getPosting_returnsNullParticipationAndApplyAction_whenNoParticipation() {
        Long userId = 1L;
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, PostingCategory.ENVIRONMENT);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(regionWithId(2L, "동구")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(List.of());
        when(postingParticipationRepository.findByUserIdAndPostingId(userId, 1L))
                .thenReturn(Optional.empty());

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(userId);

            PostingResponse response = postingService.getPosting(1L);

            assertThat(response.participationStatus()).isNull();
            assertThat(response.participationAction()).isEqualTo(PostingParticipationAction.APPLY);
        }
    }

    @ParameterizedTest
    @CsvSource({"APPLIED, CANCEL", "CONFIRMED, CANCEL", "COMPLETED, NONE", "REVIEWED, NONE"})
    @DisplayName(
            "getPosting derives participationAction from status when the activity has not ended"
                    + " yet")
    void getPosting_derivesParticipationAction_fromStatus_whenActivityNotEnded(
            PostingParticipationStatus status, PostingParticipationAction expectedAction) {
        Long userId = 1L;
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, PostingCategory.ENVIRONMENT);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(regionWithId(2L, "동구")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(List.of());
        when(postingParticipationRepository.findByUserIdAndPostingId(userId, 1L))
                .thenReturn(Optional.of(participationWithStatus(userId, 1L, status)));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(userId);

            PostingResponse response = postingService.getPosting(1L);

            assertThat(response.participationStatus()).isEqualTo(status);
            assertThat(response.participationAction()).isEqualTo(expectedAction);
        }
    }

    @ParameterizedTest
    @CsvSource({"APPLIED, COMPLETE", "CONFIRMED, COMPLETE", "COMPLETED, NONE", "REVIEWED, NONE"})
    @DisplayName(
            "getPosting derives participationAction from status once the activity end date has"
                    + " passed")
    void getPosting_derivesParticipationAction_fromStatus_whenActivityEnded(
            PostingParticipationStatus status, PostingParticipationAction expectedAction) {
        Long userId = 1L;
        Posting posting = postingWithActEndDate(1L, "동구 환경정화 봉사", 2L, PostingCategory.ENVIRONMENT);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(regionWithId(2L, "동구")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(List.of());
        when(postingParticipationRepository.findByUserIdAndPostingId(userId, 1L))
                .thenReturn(
                        Optional.of(
                                participationWithStatus(
                                        userId, 1L, status, LocalDate.now().minusDays(1))));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserIdOrNull).thenReturn(userId);

            PostingResponse response = postingService.getPosting(1L);

            assertThat(response.participationStatus()).isEqualTo(status);
            assertThat(response.participationAction()).isEqualTo(expectedAction);
        }
    }

    @Test
    @DisplayName("getPosting throws POSTING_NOT_FOUND when id does not exist")
    void getPosting_throwsPostingNotFound_whenMissing() {
        when(postingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postingService.getPosting(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.POSTING_NOT_FOUND));
    }

    @Test
    @DisplayName("getPosting leaves regionName null when regionId is null")
    void getPosting_regionNameNull_whenRegionIdNull() {
        Posting posting = postingWithId(1L, "무지역 공고", null, PostingCategory.ENVIRONMENT);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(List.of());

        PostingResponse response = postingService.getPosting(1L);

        assertThat(response.regionName()).isNull();
        assertThat(response.locations()).hasSize(1);
    }

    @Test
    @DisplayName("getPostingsMap returns an empty list when nothing matches")
    void getPostingsMap_returnsEmptyList_whenNoPostingsMatch() {
        when(postingRepository.searchForMap(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        List<com.gather.gather.domain.posting.dto.PostingMapItem> result =
                postingService.getPostingsMap(
                        null,
                        null,
                        null,
                        null,
                        java.math.BigDecimal.valueOf(37.50),
                        java.math.BigDecimal.valueOf(126.80),
                        java.math.BigDecimal.valueOf(37.60),
                        java.math.BigDecimal.valueOf(126.95));

        assertThat(result).isEmpty();
        verify(postingLocationRepository, never())
                .findAllByPostingIdInOrderByPostingIdAscLocationSeqAsc(any());
    }

    @Test
    @DisplayName("getPostingsMap includes the primary location and resolves regionName")
    void getPostingsMap_includesPrimaryLocation_andResolvesRegionName() {
        PostingMapRow row = postingMapRow(1L, 2L);
        when(postingRepository.searchForMap(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));
        when(regionRepository.findAllById(any())).thenReturn(List.of(regionWithId(2L, "동구")));
        when(postingLocationRepository.findAllByPostingIdInOrderByPostingIdAscLocationSeqAsc(any()))
                .thenReturn(List.of());

        List<com.gather.gather.domain.posting.dto.PostingMapItem> result =
                postingService.getPostingsMap(
                        null,
                        null,
                        null,
                        null,
                        java.math.BigDecimal.valueOf(37.50),
                        java.math.BigDecimal.valueOf(126.80),
                        java.math.BigDecimal.valueOf(37.60),
                        java.math.BigDecimal.valueOf(126.95));

        assertThat(result).hasSize(1);
        com.gather.gather.domain.posting.dto.PostingMapItem item = result.get(0);
        assertThat(item.id()).isEqualTo(1L);
        assertThat(item.regionName()).isEqualTo("동구");
        assertThat(item.locations()).hasSize(1);
        assertThat(item.locations().get(0).locationSeq()).isEqualTo(1);
    }

    @Test
    @DisplayName("getPostingsMap appends a secondary location that has valid coordinates")
    void getPostingsMap_appendsSecondaryLocation_whenCoordinatesArePresent() {
        PostingMapRow row = postingMapRow(1L, null);
        when(postingRepository.searchForMap(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));
        when(regionRepository.findAllById(any())).thenReturn(List.of());
        when(postingLocationRepository.findAllByPostingIdInOrderByPostingIdAscLocationSeqAsc(any()))
                .thenReturn(
                        List.of(
                                locationWithLatLng(
                                        2,
                                        "2번째 장소",
                                        java.math.BigDecimal.valueOf(37.56),
                                        java.math.BigDecimal.valueOf(126.91))));

        List<com.gather.gather.domain.posting.dto.PostingMapItem> result =
                postingService.getPostingsMap(
                        null,
                        null,
                        null,
                        null,
                        java.math.BigDecimal.valueOf(37.50),
                        java.math.BigDecimal.valueOf(126.80),
                        java.math.BigDecimal.valueOf(37.60),
                        java.math.BigDecimal.valueOf(126.95));

        assertThat(result.get(0).locations()).hasSize(2);
        assertThat(result.get(0).locations().get(1).locationSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName(
            "getPostingsMap excludes a secondary location without coordinates (B1: contract says"
                    + " locations without lat/lng are omitted)")
    void getPostingsMap_excludesSecondaryLocation_whenCoordinatesAreMissing() {
        PostingMapRow row = postingMapRow(1L, null);
        when(postingRepository.searchForMap(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));
        when(regionRepository.findAllById(any())).thenReturn(List.of());
        when(postingLocationRepository.findAllByPostingIdInOrderByPostingIdAscLocationSeqAsc(any()))
                .thenReturn(
                        List.of(
                                locationWithLatLng(
                                        2,
                                        "좌표 있는 장소",
                                        java.math.BigDecimal.valueOf(37.56),
                                        java.math.BigDecimal.valueOf(126.91)),
                                locationWithId(3, "좌표 없는 장소")));

        List<com.gather.gather.domain.posting.dto.PostingMapItem> result =
                postingService.getPostingsMap(
                        null,
                        null,
                        null,
                        null,
                        java.math.BigDecimal.valueOf(37.50),
                        java.math.BigDecimal.valueOf(126.80),
                        java.math.BigDecimal.valueOf(37.60),
                        java.math.BigDecimal.valueOf(126.95));

        assertThat(result.get(0).locations()).hasSize(2);
        assertThat(result.get(0).locations())
                .extracting(com.gather.gather.domain.posting.dto.PostingLocationResponse::locationSeq)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName(
            "getPostingsMap includes only the valid secondary location when the primary location"
                    + " has no coordinates")
    void getPostingsMap_includesOnlyValidSecondaryLocation_whenPrimaryHasNoCoordinates() {
        PostingMapRow row = postingMapRow(1L, null, false);
        when(postingRepository.searchForMap(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));
        when(regionRepository.findAllById(any())).thenReturn(List.of());
        when(postingLocationRepository.findAllByPostingIdInOrderByPostingIdAscLocationSeqAsc(any()))
                .thenReturn(
                        List.of(
                                locationWithLatLng(
                                        2,
                                        "좌표 있는 장소",
                                        java.math.BigDecimal.valueOf(37.56),
                                        java.math.BigDecimal.valueOf(126.91))));

        List<com.gather.gather.domain.posting.dto.PostingMapItem> result =
                postingService.getPostingsMap(
                        null,
                        null,
                        null,
                        null,
                        java.math.BigDecimal.valueOf(37.50),
                        java.math.BigDecimal.valueOf(126.80),
                        java.math.BigDecimal.valueOf(37.60),
                        java.math.BigDecimal.valueOf(126.95));

        assertThat(result.get(0).locations()).hasSize(1);
        assertThat(result.get(0).locations().get(0).locationSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("getPostingsMap resolves regionId to itself plus children before querying")
    void getPostingsMap_resolvesRegionHierarchy_whenRegionIdProvided() {
        when(regionRepository.findIdsIncludingChildren(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(postingRepository.searchForMap(
                        eq(List.of(1L, 2L, 3L)), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        postingService.getPostingsMap(
                1L,
                null,
                null,
                null,
                java.math.BigDecimal.valueOf(37.50),
                java.math.BigDecimal.valueOf(126.80),
                java.math.BigDecimal.valueOf(37.60),
                java.math.BigDecimal.valueOf(126.95));

        verify(regionRepository).findIdsIncludingChildren(1L);
        verify(postingRepository)
                .searchForMap(
                        eq(List.of(1L, 2L, 3L)), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPostingsMap throws VALIDATION_ERROR when activityStartDate is after activityEndDate")
    void getPostingsMap_throwsValidationError_whenActivityDateRangeInverted() {
        assertThatThrownBy(
                        () ->
                                postingService.getPostingsMap(
                                        null,
                                        LocalDate.of(2026, 8, 25),
                                        LocalDate.of(2026, 8, 20),
                                        null,
                                        java.math.BigDecimal.valueOf(37.50),
                                        java.math.BigDecimal.valueOf(126.80),
                                        java.math.BigDecimal.valueOf(37.60),
                                        java.math.BigDecimal.valueOf(126.95)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(postingRepository, never())
                .searchForMap(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPostingsMap throws VALIDATION_ERROR when swLat is greater than neLat")
    void getPostingsMap_throwsValidationError_whenSwLatGreaterThanNeLat() {
        assertThatThrownBy(
                        () ->
                                postingService.getPostingsMap(
                                        null,
                                        null,
                                        null,
                                        null,
                                        java.math.BigDecimal.valueOf(37.60),
                                        java.math.BigDecimal.valueOf(126.80),
                                        java.math.BigDecimal.valueOf(37.50),
                                        java.math.BigDecimal.valueOf(126.95)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(postingRepository, never())
                .searchForMap(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPostingsMap throws VALIDATION_ERROR when swLng is greater than neLng")
    void getPostingsMap_throwsValidationError_whenSwLngGreaterThanNeLng() {
        assertThatThrownBy(
                        () ->
                                postingService.getPostingsMap(
                                        null,
                                        null,
                                        null,
                                        null,
                                        java.math.BigDecimal.valueOf(37.50),
                                        java.math.BigDecimal.valueOf(126.95),
                                        java.math.BigDecimal.valueOf(37.60),
                                        java.math.BigDecimal.valueOf(126.80)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(postingRepository, never())
                .searchForMap(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPostingsMap throws VALIDATION_ERROR when a latitude is out of [-90, 90]")
    void getPostingsMap_throwsValidationError_whenLatitudeOutOfRange() {
        assertThatThrownBy(
                        () ->
                                postingService.getPostingsMap(
                                        null,
                                        null,
                                        null,
                                        null,
                                        java.math.BigDecimal.valueOf(-91),
                                        java.math.BigDecimal.valueOf(126.80),
                                        java.math.BigDecimal.valueOf(37.60),
                                        java.math.BigDecimal.valueOf(126.95)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(postingRepository, never())
                .searchForMap(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPostingsMap throws VALIDATION_ERROR when a longitude is out of [-180, 180]")
    void getPostingsMap_throwsValidationError_whenLongitudeOutOfRange() {
        assertThatThrownBy(
                        () ->
                                postingService.getPostingsMap(
                                        null,
                                        null,
                                        null,
                                        null,
                                        java.math.BigDecimal.valueOf(37.50),
                                        java.math.BigDecimal.valueOf(126.80),
                                        java.math.BigDecimal.valueOf(37.60),
                                        java.math.BigDecimal.valueOf(181)))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(postingRepository, never())
                .searchForMap(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPostingsMap allows the boundary lat/lng values -90/90/-180/180")
    void getPostingsMap_allowsBoundaryLatLngValues() {
        when(postingRepository.searchForMap(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        postingService.getPostingsMap(
                null,
                null,
                null,
                null,
                java.math.BigDecimal.valueOf(-90),
                java.math.BigDecimal.valueOf(-180),
                java.math.BigDecimal.valueOf(90),
                java.math.BigDecimal.valueOf(180));

        verify(postingRepository)
                .searchForMap(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** locationSeq=1은 항상 Posting 자신의 위·경도로 표현되므로, 이 헬퍼는 위·경도가 있는 상태로 생성한다. */
    private PostingMapRow postingMapRow(Long id, Long regionId) {
        return postingMapRow(id, regionId, true);
    }

    private PostingMapRow postingMapRow(Long id, Long regionId, boolean hasPrimaryCoordinates) {
        return new PostingMapRow(
                id,
                "지도 테스트 공고",
                "테스트 기관",
                "테스트 주소",
                regionId,
                PostingCategory.WELFARE,
                PostingStatus.RECRUITING,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 18),
                hasPrimaryCoordinates ? java.math.BigDecimal.valueOf(37.55) : null,
                hasPrimaryCoordinates ? java.math.BigDecimal.valueOf(126.90) : null);
    }

    private Posting postingWithId(Long id, String title, Long regionId, PostingCategory category) {
        Posting posting =
                Posting.builder()
                        .extId("ext-" + id)
                        .title(title)
                        .status(PostingStatus.RECRUITING)
                        .regionId(regionId)
                        .category(category)
                        .build();
        ReflectionTestUtils.setField(posting, "id", id);
        return posting;
    }

    /** 활동종료일이 어제인 공고 — isActivityEnded()가 true를 반환한다. */
    private Posting postingWithActEndDate(
            Long id, String title, Long regionId, PostingCategory category) {
        Posting posting =
                Posting.builder()
                        .extId("ext-" + id)
                        .title(title)
                        .status(PostingStatus.RECRUITING)
                        .regionId(regionId)
                        .category(category)
                        .actEndDate(LocalDate.now().minusDays(1))
                        .build();
        ReflectionTestUtils.setField(posting, "id", id);
        return posting;
    }

    private Region regionWithId(Long id, String name) {
        Region region = Region.create(name, 3, "code-" + id, null);
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }

    private PostingLocation locationWithId(int locationSeq, String address) {
        return PostingLocation.create(1L, locationSeq, address, null, null);
    }

    private PostingLocation locationWithLatLng(
            int locationSeq, String address, java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
        return PostingLocation.create(1L, locationSeq, address, latitude, longitude);
    }

    private PostingParticipation participationWithStatus(
            Long userId, Long postingId, PostingParticipationStatus status) {
        return participationWithStatus(userId, postingId, status, LocalDate.now().plusDays(30));
    }

    private PostingParticipation participationWithStatus(
            Long userId, Long postingId, PostingParticipationStatus status, LocalDate endDate) {
        PostingParticipation participation =
                PostingParticipation.create(userId, postingId, endDate, endDate);
        ReflectionTestUtils.setField(participation, "status", status);
        return participation;
    }
}
