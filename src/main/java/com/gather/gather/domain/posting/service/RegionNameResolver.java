package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** 공고 목록에 필요한 지역명을 배치로 조회한다(카드마다 개별 조회하는 N+1을 피하기 위함). */
@Component
@RequiredArgsConstructor
public class RegionNameResolver {

    private final RegionRepository regionRepository;

    public Map<Long, String> resolve(Page<Posting> postings) {
        Set<Long> regionIds =
                postings.getContent().stream()
                        .map(Posting::getRegionId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return regionRepository.findAllById(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, Region::getName));
    }
}
