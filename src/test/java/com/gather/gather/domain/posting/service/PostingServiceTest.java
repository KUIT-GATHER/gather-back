package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.category.entity.Category;
import com.gather.gather.domain.category.repository.CategoryRepository;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import java.util.List;
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
    @Mock private RegionRepository regionRepository;
    @Mock private CategoryRepository categoryRepository;

    private PostingService postingService;

    @BeforeEach
    void setUp() {
        postingService =
                new PostingService(postingRepository, regionRepository, categoryRepository);
    }

    @Test
    @DisplayName("getPostings queries only RECRUITING postings for the given pageable")
    void getPostings_queriesOnlyRecruitingStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.findByStatus(PostingStatus.RECRUITING, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        postingService.getPostings(pageable);

        verify(postingRepository).findByStatus(PostingStatus.RECRUITING, pageable);
    }

    @Test
    @DisplayName("getPostings fills regionName/categoryName when matches exist")
    void getPostings_mapsRegionAndCategoryNames_whenMatched() {
        Posting posting = postingWithId(1L, "동구 환경정화 봉사", 2L, 10L);
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.findByStatus(PostingStatus.RECRUITING, pageable))
                .thenReturn(new PageImpl<>(List.of(posting)));
        when(regionRepository.findAllById(any())).thenReturn(List.of(regionWithId(2L, "동구")));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(categoryWithId(10L, "환경")));

        PageResponse<PostingSummaryResponse> result = postingService.getPostings(pageable);

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
        when(postingRepository.findByStatus(PostingStatus.RECRUITING, pageable))
                .thenReturn(new PageImpl<>(List.of(posting)));
        when(regionRepository.findAllById(any())).thenReturn(List.of());
        when(categoryRepository.findAllById(any())).thenReturn(List.of(categoryWithId(10L, "환경")));

        PageResponse<PostingSummaryResponse> result = postingService.getPostings(pageable);

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
        when(postingRepository.findByStatus(PostingStatus.RECRUITING, pageable))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(regionRepository.findAllById(any())).thenReturn(List.of(regionWithId(2L, "동구")));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(categoryWithId(10L, "환경")));

        postingService.getPostings(pageable);

        verify(regionRepository, times(1)).findAllById(any());
        verify(categoryRepository, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("getPostings returns empty PageResponse when no postings exist")
    void getPostings_returnsEmptyPageResponse_whenNoPostingsExist() {
        Pageable pageable = PageRequest.of(0, 20);
        when(postingRepository.findByStatus(eq(PostingStatus.RECRUITING), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<PostingSummaryResponse> result = postingService.getPostings(pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
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
}
