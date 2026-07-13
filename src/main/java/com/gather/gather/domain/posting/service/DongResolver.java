package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공고 주소 텍스트에서 읍/면/동(level 4)을 찾아낸다.
 *
 * <p>1365 API는 동 코드를 별도로 주지 않아 정규식으로 동 이름을 "추측"하는 대신, 이미 구/시(level 2) 단위까지 확정된 gu의 실제 자식 읍/면/동 이름
 * 목록만 후보로 놓고 주소 텍스트에 그 이름이 그대로 포함돼 있는지 본다. 후보 목록으로 스코프를 좁혔기 때문에 일반적인 단어(예: "아동", "활동")가 동 이름으로 오인식될
 * 위험이 없다.
 *
 * <p>후보 중 이름 길이가 긴 것부터 검사해 "신도림동"이 있을 때 우연히 부분열로 겹치는 짧은 동 이름을 먼저 채택하는 일을 막는다. 필드 우선순위는 areaAddress1
 * → postAdres → actPlace 순(devplan.md §9의 주소 신뢰도 순서와 동일).
 */
@Component
@RequiredArgsConstructor
public class DongResolver {

    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public Long resolve(Long guRegionId, String areaAddress1, String postAdres, String actPlace) {
        if (guRegionId == null) {
            return null;
        }
        Region gu = regionRepository.findById(guRegionId).orElse(null);
        if (gu == null || gu.getLevel() == null || gu.getLevel() != 2) {
            return null;
        }

        List<Region> candidates = regionRepository.findByParentId(guRegionId);
        if (candidates.isEmpty()) {
            return null;
        }
        List<Region> byLengthDesc =
                candidates.stream()
                        .sorted(
                                Comparator.comparingInt((Region r) -> r.getName().length())
                                        .reversed())
                        .toList();

        for (String text : new String[] {areaAddress1, postAdres, actPlace}) {
            if (text == null || text.isBlank()) {
                continue;
            }
            for (Region candidate : byLengthDesc) {
                if (text.contains(candidate.getName())) {
                    return candidate.getId();
                }
            }
        }
        return null;
    }

    /** regionId가 guRegionId 소속 읍/면/동(level 4)인지 확인한다. 갱신 시 이미 확정된 동 단위 정밀도를 구 단위로 되돌리지 않기 위함. */
    @Transactional(readOnly = true)
    public boolean isDongOf(Long regionId, Long guRegionId) {
        if (regionId == null || guRegionId == null) {
            return false;
        }
        return regionRepository
                .findById(regionId)
                .filter(r -> r.getLevel() != null && r.getLevel() == 4)
                .filter(r -> r.getParent() != null)
                .map(r -> guRegionId.equals(r.getParent().getId()))
                .orElse(false);
    }
}
