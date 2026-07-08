package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.category.entity.Category;
import com.gather.gather.domain.category.repository.CategoryRepository;
import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingLocation;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingLocationRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostingServiceTest {

    @Mock private PostingRepository postingRepository;
    @Mock private PostingLocationRepository postingLocationRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private CategoryRepository categoryRepository;

    private PostingService postingService;

    @BeforeEach
    void setUp() {
        postingService =
                new PostingService(
                        postingRepository,
                        postingLocationRepository,
                        regionRepository,
                        categoryRepository);
    }

    @Test
    @DisplayName("getPostings defaults to RECRUITING when status is not provided")
    void getPostings_defaultsToRecruiting_whenStatusNull() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(
                        eq(PostingStatus.RECRUITING), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, null, null, null, null);

        verify(postingRepository).search(PostingStatus.RECRUITING, null, null, null, pageable);
        verify(regionRepository, never()).findIdsIncludingChildren(any());
    }

    @Test
    @DisplayName("getPostings uses the given status instead of the RECRUITING default")
    void getPostings_usesGivenStatus_whenProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(
                        eq(PostingStatus.CLOSED), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, null, PostingStatus.CLOSED, null, null);

        verify(postingRepository).search(PostingStatus.CLOSED, null, null, null, pageable);
    }

    @Test
    @DisplayName("getPostings resolves regionId to itself plus children before querying")
    void getPostings_resolvesRegionHierarchy_whenRegionIdProvided() {
        Pageable pageable = PageRequest.of(0, 20);
        when(regionRepository.findIdsIncludingChildren(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(postingRepository.search(
                        eq(PostingStatus.RECRUITING),
                        eq(List.of(1L, 2L, 3L)),
                        isNull(),
                        isNull(),
                        eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, 1L, null, null, null);

        verify(regionRepository).findIdsIncludingChildren(1L);
        verify(postingRepository)
                .search(PostingStatus.RECRUITING, List.of(1L, 2L, 3L), null, null, pageable);
    }

    @Test
    @DisplayName("getPostings passes the notice date range through to the repository")
    void getPostings_passesNoticeDateRange() {
        Pageable pageable = PageRequest.of(0, 20);
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(postingRepository.search(PostingStatus.RECRUITING, null, from, to, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable, null, null, from, to);

        verify(postingRepository).search(PostingStatus.RECRUITING, null, from, to, pageable);
    }

    @Test
    @DisplayName("getPostings fills regionName/categoryName when matches exist")
    void getPostings_mapsRegionAndCategoryNames_whenMatched() {
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, 10L);
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(PostingStatus.RECRUITING, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(posting)));
        when(regionRepository.findAllById(any())).thenReturn(List.of(regionWithId(2L, "동구")));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(categoryWithId(10L, "환경")));

        PageResponse<PostingSummaryResponse> result =
                postingService.getPostings(pageable, null, null, null, null);

        assertThat(result.content()).hasSize(1);
        PostingSummaryResponse response = result.content().get(0);
        assertThat(response.regionName()).isEqualTo("동구");
        assertThat(response.categoryName()).isEqualTo("환경");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getPostings leaves regionName null when regionId is null or unmatched")
    void getPostings_regionNameNull_whenRegionIdNullOrUnmatched() {
        Posting posting = postingWithId(1L, "무지역 공고", null, 10L);
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(PostingStatus.RECRUITING, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(posting)));
        when(regionRepository.findAllById(any())).thenReturn(List.of());
        when(categoryRepository.findAllById(any())).thenReturn(List.of(categoryWithId(10L, "환경")));

        PageResponse<PostingSummaryResponse> result =
                postingService.getPostings(pageable, null, null, null, null);

        assertThat(result.content().get(0).regionName()).isNull();
        assertThat(result.content().get(0).categoryName()).isEqualTo("환경");
    }

    @Test
    @DisplayName(
            "getPostings batches region/category lookups exactly once regardless of item count")
    void getPostings_batchesLookups_exactlyOncePerCall() {
        Posting first = postingWithId(1L, "공고1", 2L, 10L);
        Posting second = postingWithId(2L, "공고2", 2L, 10L);
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(PostingStatus.RECRUITING, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(regionRepository.findAllById(any())).thenReturn(List.of(regionWithId(2L, "동구")));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(categoryWithId(10L, "환경")));

        postingService.getPostings(pageable, null, null, null, null);

        verify(regionRepository, times(1)).findAllById(any());
        verify(categoryRepository, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("getPostings returns empty PageResponse when no postings exist")
    void getPostings_returnsEmptyPageResponse_whenNoPostingsExist() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.search(PostingStatus.RECRUITING, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<PostingSummaryResponse> result =
                postingService.getPostings(pageable, null, null, null, null);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("getPosting returns detail with locations when posting exists")
    void getPosting_returnsDetailWithLocations_whenExists() {
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, 10L);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(regionRepository.findById(2L)).thenReturn(Optional.of(regionWithId(2L, "동구")));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryWithId(10L, "환경")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(
                        List.of(locationWithId(2, "부산시 어딘가 2"), locationWithId(3, "부산시 어딘가 3")));

        PostingResponse response = postingService.getPosting(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.regionName()).isEqualTo("동구");
        assertThat(response.categoryName()).isEqualTo("환경");
        assertThat(response.locations()).hasSize(3);
        assertThat(response.locations().get(0).locationSeq()).isEqualTo(1);
        assertThat(response.locations().get(1).locationSeq()).isEqualTo(2);
        assertThat(response.locations().get(2).locationSeq()).isEqualTo(3);
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
        Posting posting = postingWithId(1L, "무지역 공고", null, 10L);
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(categoryWithId(10L, "환경")));
        when(postingLocationRepository.findAllByPostingIdOrderByLocationSeq(1L))
                .thenReturn(List.of());

        PostingResponse response = postingService.getPosting(1L);

        assertThat(response.regionName()).isNull();
        assertThat(response.locations()).hasSize(1);
    }

    private Posting postingWithId(Long id, String title, Long regionId, Long categoryId) {
        Posting posting =
                Posting.builder()
                        .extId("ext-" + id)
                        .title(title)
                        .status(PostingStatus.RECRUITING)
                        .regionId(regionId)
                        .categoryId(categoryId)
                        .build();
        ReflectionTestUtils.setField(posting, "id", id);
        return posting;
    }

    private Region regionWithId(Long id, String name) {
        Region region = Region.create(name, 3, "code-" + id, null);
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }

    private Category categoryWithId(Long id, String name) {
        Category category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "name", name);
        return category;
    }

    private PostingLocation locationWithId(int locationSeq, String address) {
        return PostingLocation.create(1L, locationSeq, address, null, null);
    }
}
