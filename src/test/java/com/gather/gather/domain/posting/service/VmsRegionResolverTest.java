package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
class VmsRegionResolverTest {

    @Mock private RegionRepository regionRepository;

    private VmsRegionResolver vmsRegionResolver;

    @BeforeEach
    void setUp() {
        vmsRegionResolver = new VmsRegionResolver(regionRepository);
    }

    @Test
    @DisplayName("resolve matches both sido and sigungu names contained in the VMS region text")
    void resolve_matchesSidoAndSigungu() {
        Region gyeonggi = regionOf(1L, "경기도");
        Region siheung = regionOf(11L, "시흥시");
        when(regionRepository.findByParentIsNull())
                .thenReturn(List.of(regionOf(2L, "서울특별시"), gyeonggi));
        when(regionRepository.findByParentId(1L)).thenReturn(List.of(siheung));

        Long result = vmsRegionResolver.resolve("[경기]\n경기도 시흥시");

        assertThat(result).isEqualTo(11L);
    }

    @Test
    @DisplayName("resolve falls back to the sido id when no sigungu candidate matches")
    void resolve_fallsBackToSido_whenSigunguDoesNotMatch() {
        Region gyeonggi = regionOf(1L, "경기도");
        when(regionRepository.findByParentIsNull()).thenReturn(List.of(gyeonggi));
        when(regionRepository.findByParentId(1L)).thenReturn(List.of(regionOf(11L, "성남시")));

        Long result = vmsRegionResolver.resolve("[경기]\n경기도 시흥시");

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("resolve returns null when no sido candidate matches")
    void resolve_returnsNull_whenSidoDoesNotMatch() {
        when(regionRepository.findByParentIsNull())
                .thenReturn(List.of(regionOf(1L, "서울특별시"), regionOf(2L, "부산광역시")));

        Long result = vmsRegionResolver.resolve("[경기]\n경기도 시흥시");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolve returns null for blank input")
    void resolve_returnsNull_whenTextIsBlank() {
        Long result = vmsRegionResolver.resolve(" ");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolve matches 강원특별자치도 via the 강원도 rename alias when sido matching fails")
    void resolve_matchesViaRenameAlias_gangwon() {
        Region gangwon = regionOf(1L, "강원도");
        Region chuncheon = regionOf(11L, "춘천시");
        when(regionRepository.findByParentIsNull()).thenReturn(List.of(gangwon));
        when(regionRepository.findByParentId(1L)).thenReturn(List.of(chuncheon));

        Long result = vmsRegionResolver.resolve("[강원] 강원특별자치도 춘천시");

        assertThat(result).isEqualTo(11L);
    }

    @Test
    @DisplayName("resolve matches 전북특별자치도 via the 전라북도 rename alias when sido matching fails")
    void resolve_matchesViaRenameAlias_jeonbuk() {
        Region jeonbuk = regionOf(1L, "전라북도");
        Region iksan = regionOf(11L, "익산시");
        when(regionRepository.findByParentIsNull()).thenReturn(List.of(jeonbuk));
        when(regionRepository.findByParentId(1L)).thenReturn(List.of(iksan));

        Long result = vmsRegionResolver.resolve("[전북] 전북특별자치도 익산시");

        assertThat(result).isEqualTo(11L);
    }

    @Test
    @DisplayName(
            "resolve disambiguates the merged 전남광주통합특별시 alias by sigungu name, picking the actual"
                    + " owning old sido")
    void resolve_disambiguatesMergedAlias_byMatchingSigungu() {
        Region jeonnam = regionOf(1L, "전라남도");
        Region gwangju = regionOf(2L, "광주광역시");
        Region mokpo = regionOf(11L, "목포시");
        Region dong = regionOf(21L, "동구");
        // 광주광역시를 먼저 검사하도록 순서를 둬서, 그 자식(동구)에 매칭이 없을 때 실제로 전라남도까지 넘어가
        // 목포시를 찾는지(=첫 후보를 무조건 쓰는 게 아니라 진짜로 가려내는지) 검증한다.
        when(regionRepository.findByParentIsNull()).thenReturn(List.of(gwangju, jeonnam));
        when(regionRepository.findByParentId(2L)).thenReturn(List.of(dong));
        when(regionRepository.findByParentId(1L)).thenReturn(List.of(mokpo));

        Long result = vmsRegionResolver.resolve("[전남광주] 전남광주통합특별시 목포시");

        assertThat(result).isEqualTo(11L);
    }

    @Test
    @DisplayName(
            "resolve returns null for the merged alias when no sigungu candidate matches either old"
                    + " sido")
    void resolve_returnsNull_whenMergedAliasSigunguMatchesNeitherOldSido() {
        Region jeonnam = regionOf(1L, "전라남도");
        Region gwangju = regionOf(2L, "광주광역시");
        when(regionRepository.findByParentIsNull()).thenReturn(List.of(jeonnam, gwangju));
        when(regionRepository.findByParentId(1L)).thenReturn(List.of(regionOf(11L, "목포시")));
        when(regionRepository.findByParentId(2L)).thenReturn(List.of(regionOf(21L, "동구")));

        Long result = vmsRegionResolver.resolve("[전남광주] 전남광주통합특별시 여수시");

        assertThat(result).isNull();
    }

    private Region regionOf(Long id, String name) {
        Region region = Region.create(name, 1, null, null);
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }
}
