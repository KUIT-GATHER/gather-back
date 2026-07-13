package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DongResolverTest {

    @Mock private RegionRepository regionRepository;

    private DongResolver dongResolver;

    private static final Long GU_ID = 100L;

    @BeforeEach
    void setUp() {
        dongResolver = new DongResolver(regionRepository);
    }

    @Test
    @DisplayName("resolve returns the dong id when its name appears in areaAddress1")
    void resolve_matchesDongName_inAreaAddress1() {
        Region gu = regionOf(GU_ID, 2, null);
        when(regionRepository.findById(GU_ID)).thenReturn(Optional.of(gu));
        when(regionRepository.findByParentId(GU_ID))
                .thenReturn(
                        List.of(
                                regionOf(1L, 4, "구운동"),
                                regionOf(2L, 4, "인계동"),
                                regionOf(3L, 4, "매탄동")));

        Long result =
                dongResolver.resolve(GU_ID, "경기도 수원시 권선구 구운로4번길 34 (구운동, 서호노인복지관)", null, null);

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName(
            "resolve falls back through postAdres then actPlace when earlier fields don't match")
    void resolve_fallsBackThroughFields() {
        Region gu = regionOf(GU_ID, 2, null);
        when(regionRepository.findById(GU_ID)).thenReturn(Optional.of(gu));
        when(regionRepository.findByParentId(GU_ID)).thenReturn(List.of(regionOf(1L, 4, "삼각동")));

        Long result =
                dongResolver.resolve(
                        GU_ID,
                        "수요처 지도",
                        "광주광역시 북구 북부순환로 396-2 (삼각동) 법무부가온어린이집",
                        "광주광역시 북구 북부순환로 396-2 (법무부가온어린이집)");

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName(
            "resolve prefers the longest matching candidate to avoid partial-substring collisions")
    void resolve_prefersLongestCandidateName() {
        Region gu = regionOf(GU_ID, 2, null);
        when(regionRepository.findById(GU_ID)).thenReturn(Optional.of(gu));
        when(regionRepository.findByParentId(GU_ID))
                .thenReturn(List.of(regionOf(1L, 4, "도림동"), regionOf(2L, 4, "신도림동")));

        Long result = dongResolver.resolve(GU_ID, "서울특별시 구로구 신도림동 123-4", null, null);

        assertThat(result).isEqualTo(2L);
    }

    @Test
    @DisplayName(
            "resolve does not false-positive on generic words that merely end with a dong-like syllable")
    void resolve_doesNotMatch_genericWordContainingDongSyllable() {
        Region gu = regionOf(GU_ID, 2, null);
        when(regionRepository.findById(GU_ID)).thenReturn(Optional.of(gu));
        when(regionRepository.findByParentId(GU_ID)).thenReturn(List.of(regionOf(1L, 4, "마곡동")));

        Long result = dongResolver.resolve(GU_ID, null, null, "장애아동 가정(서울 강서구)");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolve returns null when no candidate name appears in any field")
    void resolve_returnsNull_whenNoTextMatches() {
        Region gu = regionOf(GU_ID, 2, null);
        when(regionRepository.findById(GU_ID)).thenReturn(Optional.of(gu));
        when(regionRepository.findByParentId(GU_ID)).thenReturn(List.of(regionOf(1L, 4, "삼각동")));

        Long result = dongResolver.resolve(GU_ID, "재택봉사", null, null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolve returns null when the given region is not a gu-level(level 2) region")
    void resolve_returnsNull_whenRegionIsNotGuLevel() {
        Region sido = regionOf(GU_ID, 1, null);
        when(regionRepository.findById(GU_ID)).thenReturn(Optional.of(sido));

        Long result = dongResolver.resolve(GU_ID, "아무 주소", null, null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolve returns null when guRegionId is null")
    void resolve_returnsNull_whenGuRegionIdIsNull() {
        Long result = dongResolver.resolve(null, "아무 주소", null, null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("isDongOf returns true only when regionId is a level-4 child of guRegionId")
    void isDongOf_true_whenRegionIsDongChildOfGu() {
        Region parent = regionOf(GU_ID, 2, null);
        Region dong = regionOf(1L, 4, "삼각동");
        ReflectionTestUtils.setField(dong, "parent", parent);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(dong));

        assertThat(dongResolver.isDongOf(1L, GU_ID)).isTrue();
    }

    @Test
    @DisplayName("isDongOf returns false when regionId belongs to a different gu")
    void isDongOf_false_whenParentGuDiffers() {
        Region otherGu = regionOf(999L, 2, null);
        Region dong = regionOf(1L, 4, "삼각동");
        ReflectionTestUtils.setField(dong, "parent", otherGu);
        when(regionRepository.findById(1L)).thenReturn(Optional.of(dong));

        assertThat(dongResolver.isDongOf(1L, GU_ID)).isFalse();
    }

    @Test
    @DisplayName("isDongOf returns false when regionId itself is gu-level, not dong-level")
    void isDongOf_false_whenRegionIsGuLevel() {
        Region gu = regionOf(GU_ID, 2, null);
        when(regionRepository.findById(GU_ID)).thenReturn(Optional.of(gu));

        assertThat(dongResolver.isDongOf(GU_ID, GU_ID)).isFalse();
    }

    @Test
    @DisplayName("isDongOf returns false when regionId is null")
    void isDongOf_false_whenRegionIdIsNull() {
        assertThat(dongResolver.isDongOf(null, GU_ID)).isFalse();
    }

    @Test
    @DisplayName("isDongOf returns false when guRegionId is null")
    void isDongOf_false_whenGuRegionIdIsNull() {
        assertThat(dongResolver.isDongOf(1L, null)).isFalse();
    }

    private Region regionOf(Long id, int level, String name) {
        Region region = Region.create(name == null ? "테스트지역" : name, level, null, null);
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }
}
