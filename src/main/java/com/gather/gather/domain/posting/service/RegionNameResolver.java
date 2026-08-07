package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * 공고/모임 목록에 필요한 지역명을 배치로 조회한다(카드마다 개별 조회하는 N+1을 피하기 위함).
 *
 * <p>목록·카드에는 시군구(level 2) 단위로 통일해 노출한다. regionId가 읍/면/동(level 4)이면 parent(level 2)의 이름을 대신 반환한다 —
 * level 4는 {@code DongResolver} 등이 검색 필터링 정밀도를 위해 저장하는 값일 뿐, 표시용으로 노출하려는 의도가 아니다.
 */
@Component
@RequiredArgsConstructor
public class RegionNameResolver {

    private static final int DONG_LEVEL = 4;

    private final RegionRepository regionRepository;

    public Map<Long, String> resolve(Page<Posting> postings) {
        return resolve(postings.getContent().stream().map(Posting::getRegionId).toList());
    }

    public Map<Long, String> resolve(Collection<Long> regionIds) {
        Set<Long> distinctRegionIds =
                regionIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        List<Region> regions = regionRepository.findAllById(distinctRegionIds);

        Set<Long> parentIdsNeeded =
                regions.stream()
                        .filter(RegionNameResolver::isDong)
                        .map(region -> region.getParent().getId())
                        .collect(Collectors.toSet());
        Map<Long, String> parentNames =
                parentIdsNeeded.isEmpty()
                        ? Map.of()
                        : regionRepository.findAllById(parentIdsNeeded).stream()
                                .collect(Collectors.toMap(Region::getId, Region::getName));

        return regions.stream()
                .collect(
                        Collectors.toMap(
                                Region::getId, region -> displayName(region, parentNames)));
    }

    private static boolean isDong(Region region) {
        return region.getLevel() != null
                && region.getLevel() == DONG_LEVEL
                && region.getParent() != null;
    }

    private static String displayName(Region region, Map<Long, String> parentNames) {
        if (isDong(region)) {
            return parentNames.getOrDefault(region.getParent().getId(), region.getName());
        }
        return region.getName();
    }
}
