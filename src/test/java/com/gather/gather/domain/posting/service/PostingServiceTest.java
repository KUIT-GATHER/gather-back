package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.dto.PostingParticipationAction;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingLocation;
import com.gather.gather.domain.posting.entity.PostingParticipation;
import com.gather.gather.domain.posting.entity.PostingParticipationStatus;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingLocationRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.time.LocalDate;
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
import org.springframework.data.domain.PageImpl;
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
                        postingParticipationRepository);
    }

    @Test
    @DisplayName("getPostings passes a null status through to the repository unchanged")
    void getPostings_passesNullStatusThrough_whenStatusNotProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(
                        isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, null, null, null, null, null, null, null);

        verify(postingRepository).search(null, null, null, null, null, null, pageable);
        verify(regionRepository, never()).findIdsIncludingChildren(any());
        verify(regionRepository, never()).findIdsIncludingChildrenByGroupId(any());
    }

    @Test
    @DisplayName("getPostings passes an explicitly given status through unchanged")
    void getPostings_usesGivenStatus_whenProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(
                        eq(PostingStatus.CLOSED),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(
                pageable, null, null, PostingStatus.CLOSED, null, null, null, null);

        verify(postingRepository)
                .search(PostingStatus.CLOSED, null, null, null, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings resolves regionId to itself plus children before querying")
    void getPostings_resolvesRegionHierarchy_whenRegionIdProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(regionRepository.findIdsIncludingChildren(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(postingRepository.search(
                        isNull(),
                        eq(List.of(1L, 2L, 3L)),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, 1L, null, null, null, null, null, null);

        verify(regionRepository).findIdsIncludingChildren(1L);
        verify(postingRepository)
                .search(null, List.of(1L, 2L, 3L), null, null, null, null, pageable);
    }

    @Test
    @DisplayName(
            "getPostings resolves regionGroupId to every sido/gungu in that group before"
                    + " querying")
    void getPostings_resolvesRegionGroupHierarchy_whenRegionGroupIdProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(regionRepository.findIdsIncludingChildrenByGroupId(7L))
                .thenReturn(List.of(10L, 11L, 12L, 13L));
        when(postingRepository.search(
                        isNull(),
                        eq(List.of(10L, 11L, 12L, 13L)),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, null, 7L, null, null, null, null, null);

        verify(regionRepository).findIdsIncludingChildrenByGroupId(7L);
        verify(regionRepository, never()).findIdsIncludingChildren(any());
        verify(postingRepository)
                .search(null, List.of(10L, 11L, 12L, 13L), null, null, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings throws VALIDATION_ERROR when both regionId and regionGroupId given")
    void getPostings_throwsValidationError_whenBothRegionIdAndRegionGroupIdProvided() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(
                        () ->
                                postingService.getPostings(
                                        pageable, 1L, 7L, null, null, null, "환경", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(postingRepository, never()).search(any(), any(), any(), any(), any(), any(), any());
        verify(postingSearchLogService, never()).log(any());
    }

    @Test
    @DisplayName("getPostings does not log the keyword when sort validation fails")
    void getPostings_doesNotLogKeyword_whenSortInvalid() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("string"));

        assertThatThrownBy(
                        () ->
                                postingService.getPostings(
                                        pageable, null, null, null, null, null, "환경", null))
                .isInstanceOf(BusinessException.class);

        verify(postingSearchLogService, never()).log(any());
    }

    @Test
    @DisplayName("getPostings logs the keyword only after the search succeeds")
    void getPostings_logsKeyword_onlyAfterSearchSucceeds() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(null, null, null, null, "환경", null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, null, null, null, null, null, "환경", null);

        verify(postingSearchLogService).log("환경");
    }

    @Test
    @DisplayName("getPostings still returns results when search-log recording throws")
    void getPostings_returnsResults_whenSearchLoggingThrows() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(null, null, null, null, "환경", null, pageable))
                .thenReturn(new PageImpl<>(List.of()));
        doThrow(new RuntimeException("logging failed")).when(postingSearchLogService).log("환경");

        PageResponse<PostingSummaryResponse> result =
                postingService.getPostings(pageable, null, null, null, null, null, "환경", null);

        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName(
            "getPostings returns results unaffected when the keyword exceeds the search-log column length")
    void getPostings_returnsResults_whenKeywordExceedsSearchLogColumnLength() {
        String longKeyword = "가".repeat(101);
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(null, null, null, null, longKeyword, null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<PostingSummaryResponse> result =
                postingService.getPostings(
                        pageable, null, null, null, null, null, longKeyword, null);

        assertThat(result.content()).isEmpty();
        verify(postingSearchLogService).log(longKeyword);
    }

    @Test
    @DisplayName("getPostings passes the notice date range through to the repository")
    void getPostings_passesNoticeDateRange() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(postingRepository.search(null, null, from, to, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, null, null, null, from, to, null, null);

        verify(postingRepository).search(null, null, from, to, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings passes the keyword through to the repository")
    void getPostings_passesKeyword_whenProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(null, null, null, null, "환경", null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, null, null, null, null, null, "환경", null);

        verify(postingRepository).search(null, null, null, null, "환경", null, pageable);
    }

    @Test
    @DisplayName("getPostings passes the category through to the repository")
    void getPostings_passesCategory_whenProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(
                        null, null, null, null, null, PostingCategory.WELFARE, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(
                pageable, null, null, null, null, null, null, PostingCategory.WELFARE);

        verify(postingRepository)
                .search(null, null, null, null, null, PostingCategory.WELFARE, pageable);
    }

    @Test
    @DisplayName("getPostings fills regionName and passes through category when matches exist")
    void getPostings_mapsRegionNameAndCategory_whenMatched() {
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, PostingCategory.ENVIRONMENT);
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(null, null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(posting)));
        when(regionRepository.findAllById(any())).thenReturn(List.of(regionWithId(2L, "동구")));

        PageResponse<PostingSummaryResponse> result =
                postingService.getPostings(pageable, null, null, null, null, null, null, null);

        assertThat(result.content()).hasSize(1);
        PostingSummaryResponse response = result.content().get(0);
        assertThat(response.regionName()).isEqualTo("동구");
        assertThat(response.category()).isEqualTo(PostingCategory.ENVIRONMENT);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getPostings leaves regionName null when regionId is null or unmatched")
    void getPostings_regionNameNull_whenRegionIdNullOrUnmatched() {
        Posting posting = postingWithId(1L, "무지역 공고", null, PostingCategory.ENVIRONMENT);
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(null, null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(posting)));
        when(regionRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<PostingSummaryResponse> result =
                postingService.getPostings(pageable, null, null, null, null, null, null, null);

        assertThat(result.content().get(0).regionName()).isNull();
        assertThat(result.content().get(0).category()).isEqualTo(PostingCategory.ENVIRONMENT);
    }

    @Test
    @DisplayName("getPostings batches region lookups exactly once regardless of item count")
    void getPostings_batchesRegionLookup_exactlyOncePerCall() {
        Posting first = postingWithId(1L, "공고1", 2L, PostingCategory.ENVIRONMENT);
        Posting second = postingWithId(2L, "공고2", 2L, PostingCategory.ENVIRONMENT);
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(null, null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(regionRepository.findAllById(any())).thenReturn(List.of(regionWithId(2L, "동구")));

        postingService.getPostings(pageable, null, null, null, null, null, null, null);

        verify(regionRepository, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("getPostings throws VALIDATION_ERROR when sort property does not exist on Posting")
    void getPostings_throwsValidationError_whenSortPropertyUnknown() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("string"));

        assertThatThrownBy(
                        () ->
                                postingService.getPostings(
                                        pageable, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(postingRepository, never()).search(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPostings allows sorting by a known Posting property")
    void getPostings_allowsSort_whenPropertyKnown() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("title").ascending());
        when(postingRepository.search(null, null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, null, null, null, null, null, null, null);

        verify(postingRepository).search(null, null, null, null, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings returns empty PageResponse when no postings exist")
    void getPostings_returnsEmptyPageResponse_whenNoPostingsExist() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(null, null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<PostingSummaryResponse> result =
                postingService.getPostings(pageable, null, null, null, null, null, null, null);

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
    @CsvSource({"APPLIED, CANCEL", "CONFIRMED, COMPLETE", "COMPLETED, NONE", "REVIEWED, NONE"})
    @DisplayName("getPosting derives participationAction from the user's participation status")
    void getPosting_derivesParticipationAction_fromStatus(
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

    private Region regionWithId(Long id, String name) {
        Region region = Region.create(name, 3, "code-" + id, null);
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }

    private PostingLocation locationWithId(int locationSeq, String address) {
        return PostingLocation.create(1L, locationSeq, address, null, null);
    }

    private PostingParticipation participationWithStatus(
            Long userId, Long postingId, PostingParticipationStatus status) {
        PostingParticipation participation = PostingParticipation.create(userId, postingId);
        ReflectionTestUtils.setField(participation, "status", status);
        return participation;
    }
}
