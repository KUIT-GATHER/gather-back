package com.gather.gather.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.region.dto.RegionGroupResponse;
import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.entity.RegionGroup;
import com.gather.gather.domain.region.repository.RegionGroupRepository;
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

    @Mock private RegionGroupRepository regionGroupRepository;

    private RegionService regionService;

    @BeforeEach
    void setUp() {
        regionService = new RegionService(regionRepository, regionGroupRepository);
    }

    @Test
    @DisplayName(
            "getRegions returns all regions mapped to response, with null and non-null parentId"
                    + " and regionGroupId")
    void getRegions_returnsAllRegionsMappedToResponse() {
        RegionGroup seoulGroup = RegionGroup.create("GRP_SEOUL", "서울", 1);
        ReflectionTestUtils.setField(seoulGroup, "id", 1L);
        Region seoul = Region.create("서울특별시", 1, "6110000", null);
        ReflectionTestUtils.setField(seoul, "id", 1L);
        ReflectionTestUtils.setField(seoul, "regionGroup", seoulGroup);
        Region gangnam = Region.create("강남구", 2, "3220000", seoul);
        ReflectionTestUtils.setField(gangnam, "id", 2L);

        when(regionRepository.findAllWithParent()).thenReturn(List.of(seoul, gangnam));

        List<RegionResponse> result = regionService.getRegions();

        assertThat(result).hasSize(2);
        assertThat(result.get(0))
                .isEqualTo(new RegionResponse(1L, "서울특별시", 1, "6110000", null, 1L));
        assertThat(result.get(1)).isEqualTo(new RegionResponse(2L, "강남구", 2, "3220000", 1L, null));
        verify(regionRepository).findAllWithParent();
    }

    @Test
    @DisplayName("getRegions returns empty list when no regions exist")
    void getRegions_returnsEmptyList_whenNoRegionsExist() {
        when(regionRepository.findAllWithParent()).thenReturn(List.of());

        List<RegionResponse> result = regionService.getRegions();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getRegionGroups returns all groups mapped to response in sortOrder")
    void getRegionGroups_returnsAllGroupsMappedToResponse() {
        RegionGroup seoulGroup = RegionGroup.create("GRP_SEOUL", "서울", 1);
        ReflectionTestUtils.setField(seoulGroup, "id", 1L);
        RegionGroup gyeongsangGroup = RegionGroup.create("GRP_GYEONGSANG", "경상", 7);
        ReflectionTestUtils.setField(gyeongsangGroup, "id", 7L);

        when(regionGroupRepository.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(seoulGroup, gyeongsangGroup));

        List<RegionGroupResponse> result = regionService.getRegionGroups();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new RegionGroupResponse(1L, "GRP_SEOUL", "서울"));
        assertThat(result.get(1)).isEqualTo(new RegionGroupResponse(7L, "GRP_GYEONGSANG", "경상"));
        verify(regionGroupRepository).findAllByOrderBySortOrderAsc();
    }

    @Test
    @DisplayName("getRegionGroups returns empty list when no groups exist")
    void getRegionGroups_returnsEmptyList_whenNoGroupsExist() {
        when(regionGroupRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        List<RegionGroupResponse> result = regionService.getRegionGroups();

        assertThat(result).isEmpty();
    }
}
