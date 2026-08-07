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

    private Region regionOf(Long id, String name) {
        Region region = Region.create(name, 1, null, null);
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }
}
