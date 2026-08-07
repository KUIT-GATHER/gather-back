package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * VMS 상세페이지의 "봉사지역" 텍스트(예: "[경기] 경기도 시흥시")에서 시도·시군구를 찾아낸다.
 *
 * <p>VMS 자체 지역코드(area/areagugun)는 우리 {@code Region.code}(1365 sidoCd/gugunCd 전용)와 체계가 달라 코드로 매칭하지
 * 않는다. 대신 {@link DongResolver}와 같은 방식으로, 이미 후보를 좁힌 범위 안에서 이름이 텍스트에 그대로 포함되는지만 확인한다(이름 길이가 긴 후보부터
 * 검사해 짧은 이름이 우연히 부분열로 겹치는 오매칭을 방지).
 */
@Component
@RequiredArgsConstructor
public class VmsRegionResolver {

    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public Long resolve(String vmsAreaText) {
        if (vmsAreaText == null || vmsAreaText.isBlank()) {
            return null;
        }

        Region sido = findMatch(regionRepository.findByParentIsNull(), vmsAreaText);
        if (sido == null) {
            return null;
        }

        Region sigungu = findMatch(regionRepository.findByParentId(sido.getId()), vmsAreaText);
        return sigungu != null ? sigungu.getId() : sido.getId();
    }

    private Region findMatch(List<Region> candidates, String text) {
        return candidates.stream()
                .sorted(Comparator.comparingInt((Region r) -> r.getName().length()).reversed())
                .filter(candidate -> text.contains(candidate.getName()))
                .findFirst()
                .orElse(null);
    }
}
