package com.gather.gather.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegionServiceTest {

    @Mock private RegionRepository regionRepository;

    private RegionService regionService;

    @BeforeEach
    void setUp() {
        regionService = new RegionService(regionRepository);
    }

    @Test
    @DisplayName("getRegions returns all regions mapped to response, with null and non-null parentId")
    void getRegions_returnsAllRegionsMappedToResponse() {
        Region seoul = Region.create("서울특별시", 1, "11", null);
        ReflectionTestUtils.setField(seoul, "id", 1L);
        Region gangnam = Region.create("강남구", 3, "11680", seoul);
        ReflectionTestUtils.setField(gangnam, "id", 2L);

        when(regionRepository.findAll()).thenReturn(List.of(seoul, gangnam));

        List<RegionResponse> result = regionService.getRegions();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new RegionResponse(1L, "서울특별시", 1, "11", null));
        assertThat(result.get(1)).isEqualTo(new RegionResponse(2L, "강남구", 3, "11680", 1L));
        verify(regionRepository).findAll();
    }

    @Test
    @DisplayName("getRegions returns empty list when no regions exist")
    void getRegions_returnsEmptyList_whenNoRegionsExist() {
        when(regionRepository.findAll()).thenReturn(List.of());

        List<RegionResponse> result = regionService.getRegions();

        assertThat(result).isEmpty();
    }
}
