package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gather.gather.domain.meeting.repository.MeetingImageRepository;
import com.gather.gather.domain.meeting.service.MeetingImageUrlResolver;
import com.gather.gather.domain.posting.dto.PostingListItem;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingLocationRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository;
import com.gather.gather.domain.posting.repository.UnifiedPostingQueryRepository.CursorSearchResult;
import com.gather.gather.domain.posting.repository.UnifiedPostingRow;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.CursorPageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class PostingServiceTest {

    private static final Sort ID_DESC = Sort.by(Sort.Direction.DESC, "id");

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
        // 이 클래스의 대부분 테스트는 ID_DESC(정렬: id)를 기본으로 쓰므로, validateSort가 호출하는
        // isSortable("id")를 여기서 한 번에 true로 stub한다. lenient()를 쓰는 이유는 sort 검증 자체를
        // 다루는 일부 테스트(예: 알 수 없는 정렬 프로퍼티 거부 테스트)는 이 stub을 아예 쓰지 않기 때문이다.
        org.mockito.Mockito.lenient()
                .when(unifiedPostingQueryRepository.isSortable("id"))
                .thenReturn(true);
    }

    private CursorSearchResult emptyResult() {
        return new CursorSearchResult(List.of(), null, false);
    }

    @Test
    @DisplayName("getPostings passes a null status through to the repository unchanged")
    void getPostings_passesNullStatusThrough_whenStatusNotProvided() {
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, null, 20, null, null, null, null, null, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, null, null, null, null, ID_DESC, null, 20);
        verify(regionRepository, never()).findIdsIncludingChildren(any());
        verify(regionRepository, never()).findIdsIncludingChildrenByGroupId(any());
    }

    @Test
    @DisplayName("getPostings passes an explicitly given status through unchanged")
    void getPostings_usesGivenStatus_whenProvided() {
        when(unifiedPostingQueryRepository.search(
                        eq(PostingStatus.CLOSED),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC,
                null,
                20,
                null,
                null,
                PostingStatus.CLOSED,
                null,
                null,
                null,
                null,
                null,
                null);

        verify(unifiedPostingQueryRepository)
                .search(
                        PostingStatus.CLOSED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        ID_DESC,
                        null,
                        20);
    }

    @Test
    @DisplayName("getPostings resolves regionId to itself plus children before querying")
    void getPostings_resolvesRegionHierarchy_whenRegionIdProvided() {
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
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, null, 20, 1L, null, null, null, null, null, null, null, null);

        verify(regionRepository).findIdsIncludingChildren(1L);
        verify(unifiedPostingQueryRepository)
                .search(
                        null,
                        List.of(1L, 2L, 3L),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        ID_DESC,
                        null,
                        20);
    }

    @Test
    @DisplayName(
            "getPostings resolves regionGroupId to every sido/gungu in that group before querying")
    void getPostings_resolvesRegionGroupHierarchy_whenRegionGroupIdProvided() {
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
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, null, 20, null, 7L, null, null, null, null, null, null, null);

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
                        ID_DESC,
                        null,
                        20);
    }

    @Test
    @DisplayName("getPostings throws VALIDATION_ERROR when both regionId and regionGroupId given")
    void getPostings_throwsValidationError_whenBothRegionIdAndRegionGroupIdProvided() {
        assertThatThrownBy(
                        () ->
                                postingService.getPostings(
                                        ID_DESC, null, 20, 1L, 7L, null, null, null, null, null,
                                        "환경", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(unifiedPostingQueryRepository, never())
                .search(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                        anyInt());
        verify(postingSearchLogService, never()).log(any());
    }

    @Test
    @DisplayName("getPostings throws VALIDATION_ERROR when sort property is not whitelisted")
    void getPostings_throwsValidationError_whenSortPropertyUnknown() {
        Sort invalidSort = Sort.by("string");

        assertThatThrownBy(
                        () ->
                                postingService.getPostings(
                                        invalidSort,
                                        null,
                                        20,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        "환경",
                                        null))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(postingSearchLogService, never()).log(any());
        verify(unifiedPostingQueryRepository, never())
                .search(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                        anyInt());
    }

    @Test
    @DisplayName("getPostings allows sorting by a whitelisted property")
    void getPostings_allowsSort_whenPropertyKnown() {
        Sort activityStartAtAsc = Sort.by("activityStartAt").ascending();
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
                        eq(activityStartAtAsc),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                activityStartAtAsc, null, 20, null, null, null, null, null, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        activityStartAtAsc,
                        null,
                        20);
    }

    @Test
    @DisplayName("getPostings logs the keyword only after the search succeeds")
    void getPostings_logsKeyword_onlyAfterSearchSucceeds() {
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("환경"),
                        isNull(),
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, null, 20, null, null, null, null, null, null, null, "환경", null);

        verify(postingSearchLogService).log("환경");
    }

    @Test
    @DisplayName("getPostings still returns results when search-log recording throws")
    void getPostings_returnsResults_whenSearchLoggingThrows() {
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq("환경"),
                        isNull(),
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());
        org.mockito.Mockito.doThrow(new RuntimeException("logging failed"))
                .when(postingSearchLogService)
                .log("환경");

        CursorPageResponse<PostingListItem> result =
                postingService.getPostings(
                        ID_DESC, null, 20, null, null, null, null, null, null, null, "환경", null);

        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("getPostings passes the notice date range through to the repository")
    void getPostings_passesNoticeDateRange() {
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
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, null, 20, null, null, null, from, to, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, from, to, null, null, null, null, ID_DESC, null, 20);
    }

    @Test
    @DisplayName("getPostings passes the activity date range through to the repository")
    void getPostings_passesActivityDateRange() {
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
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, null, 20, null, null, null, null, null, from, to, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, from, to, null, null, ID_DESC, null, 20);
    }

    @Test
    @DisplayName("getPostings allows activityStartDate equal to activityEndDate")
    void getPostings_allowsActivityDateRange_whenStartEqualsEnd() {
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
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, null, 20, null, null, null, null, null, same, same, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, same, same, null, null, ID_DESC, null, 20);
    }

    @Test
    @DisplayName(
            "getPostings throws VALIDATION_ERROR when activityStartDate is after activityEndDate")
    void getPostings_throwsValidationError_whenActivityDateRangeInverted() {
        LocalDate start = LocalDate.of(2026, 8, 25);
        LocalDate end = LocalDate.of(2026, 8, 20);

        assertThatThrownBy(
                        () ->
                                postingService.getPostings(
                                        ID_DESC, null, 20, null, null, null, null, null, start, end,
                                        null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        ex ->
                                assertThat(((BusinessException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(unifiedPostingQueryRepository, never())
                .search(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                        anyInt());
    }

    @Test
    @DisplayName("getPostings passes the category through to the repository")
    void getPostings_passesCategory_whenProvided() {
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(PostingCategory.WELFARE),
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC,
                null,
                20,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PostingCategory.WELFARE);

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
                        ID_DESC,
                        null,
                        20);
    }

    @Test
    @DisplayName("getPostings passes the cursor through to the repository unchanged")
    void getPostings_passesCursorThrough() {
        String cursor = "abc123";
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(ID_DESC),
                        eq(cursor),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, cursor, 20, null, null, null, null, null, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, null, null, null, null, ID_DESC, cursor, 20);
    }

    @Test
    @DisplayName("getPostings falls back to the default size when size is zero or negative")
    void getPostings_fallsBackToDefaultSize_whenSizeNotPositive() {
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, null, 0, null, null, null, null, null, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, null, null, null, null, ID_DESC, null, 20);
    }

    @Test
    @DisplayName("getPostings clamps size to the maximum when a larger size is requested")
    void getPostings_clampsSize_whenSizeExceedsMax() {
        when(unifiedPostingQueryRepository.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(ID_DESC),
                        isNull(),
                        eq(100)))
                .thenReturn(emptyResult());

        postingService.getPostings(
                ID_DESC, null, 500, null, null, null, null, null, null, null, null, null);

        verify(unifiedPostingQueryRepository)
                .search(null, null, null, null, null, null, null, null, ID_DESC, null, 100);
    }

    @Test
    @DisplayName(
            "getPostings maps a POSTING-sourced row to a PostingListItem with a null thumbnail")
    void getPostings_mapsPostingSourcedRow_withNullThumbnail() {
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
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(ID_DESC),
                        isNull(),
                        eq(20)))
                .thenReturn(new CursorSearchResult(List.of(row), null, false));

        CursorPageResponse<PostingListItem> result =
                postingService.getPostings(
                        ID_DESC, null, 20, null, null, null, null, null, null, null, null, null);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).thumbnailUrl()).isNull();
    }
}
