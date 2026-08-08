package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegionNameResolverTest {

    @Mock private RegionRepository regionRepository;

    private RegionNameResolver regionNameResolver;

    @BeforeEach
    void setUp() {
        regionNameResolver = new RegionNameResolver(regionRepository);
    }

    @Test
    @DisplayName("resolve returns a level-2(시군구) region's own name unchanged")
    void resolve_returnsOwnName_forLevel2Region() {
        Region gu = regionOf(1L, 2, "강남구", null);
        when(regionRepository.findAllById(anyCollection())).thenReturn(List.of(gu));

        Map<Long, String> result = regionNameResolver.resolve(List.of(1L));

        assertThat(result).containsEntry(1L, "강남구");
        verify(regionRepository, times(1)).findAllById(anyCollection());
    }

    @Test
    @DisplayName("resolve replaces a level-4(읍/면/동) region's name with its level-2 parent's name")
    void resolve_returnsParentName_forDongRegion() {
        Region gu = regionOf(1L, 2, "강남구", null);
        Region dong = regionOf(2L, 4, "역삼동", gu);
        when(regionRepository.findAllById(anyCollection())).thenReturn(List.of(dong), List.of(gu));

        Map<Long, String> result = regionNameResolver.resolve(List.of(2L));

        assertThat(result).containsEntry(2L, "강남구");
        verify(regionRepository, times(2)).findAllById(anyCollection());
    }

    @Test
    @DisplayName("resolve falls back to the dong's own name when it has no parent (defensive)")
    void resolve_fallsBackToOwnName_whenDongHasNoParent() {
        Region dong = regionOf(2L, 4, "역삼동", null);
        when(regionRepository.findAllById(anyCollection())).thenReturn(List.of(dong));

        Map<Long, String> result = regionNameResolver.resolve(List.of(2L));

        assertThat(result).containsEntry(2L, "역삼동");
    }

    @Test
    @DisplayName("resolve does not issue a parent lookup when no dong-level region is present")
    void resolve_skipsParentLookup_whenNoDongLevelRegions() {
        Region sido = regionOf(1L, 1, "경기도", null);
        Region gu = regionOf(2L, 2, "수원시", null);
        when(regionRepository.findAllById(anyCollection())).thenReturn(List.of(sido, gu));

        Map<Long, String> result = regionNameResolver.resolve(List.of(1L, 2L));

        assertThat(result).containsEntry(1L, "경기도").containsEntry(2L, "수원시");
        verify(regionRepository, times(1)).findAllById(anyCollection());
    }

    @Test
    @DisplayName("resolve resolves a mixed batch of level-2 and level-4 regions correctly")
    void resolve_mixedBatch_resolvesEachCorrectly() {
        Region gu = regionOf(1L, 2, "강남구", null);
        Region otherGu = regionOf(3L, 2, "수원시", null);
        Region dong = regionOf(2L, 4, "역삼동", gu);
        when(regionRepository.findAllById(anyCollection()))
                .thenReturn(List.of(gu, otherGu, dong), List.of(gu));

        Map<Long, String> result = regionNameResolver.resolve(List.of(1L, 2L, 3L));

        assertThat(result)
                .containsEntry(1L, "강남구")
                .containsEntry(2L, "강남구")
                .containsEntry(3L, "수원시");
    }

    @Test
    @DisplayName("resolve returns an empty map for empty input (no dong-level regions to resolve)")
    void resolve_returnsEmptyMap_whenNoRegionIds() {
        when(regionRepository.findAllById(anyCollection())).thenReturn(List.of());

        Map<Long, String> result = regionNameResolver.resolve(List.of());

        assertThat(result).isEmpty();
        verify(regionRepository, times(1)).findAllById(anyCollection());
    }

    private Region regionOf(Long id, int level, String name, Region parent) {
        Region region = Region.create(name, level, null, parent);
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }
}
