package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    /**
     * 2026년 행정구역 개편(강원특별자치도·전북특별자치도 전환, 전남·광주 통합)으로 VMS는 신명칭을 쓰지만, 이 프로젝트 region 시드(V4)는 1365 API와의
     * 매칭을 위해 구명칭을 그대로 유지한다(V4 마이그레이션 주석 참고). 시도 단계 매칭이 실패했을 때만 시도하는 폴백이며, 구명칭 매칭을 대체하지 않는다. 전남·광주는
     * 통합으로 시도 자체가 사라져 후보가 2개(전라남도/광주광역시)라 시군구 이름으로 실제 소속을 가려낸다.
     */
    private static final Map<String, List<String>> SIDO_RENAME_ALIASES =
            Map.of(
                    "강원특별자치도", List.of("강원도"),
                    "전북특별자치도", List.of("전라북도"),
                    "전남광주통합특별시", List.of("전라남도", "광주광역시"));

    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public Long resolve(String vmsAreaText) {
        if (vmsAreaText == null || vmsAreaText.isBlank()) {
            return null;
        }

        List<Region> sidoCandidates = regionRepository.findByParentIsNull();
        Region sido = findMatch(sidoCandidates, vmsAreaText);
        if (sido != null) {
            Region sigungu = findMatch(regionRepository.findByParentId(sido.getId()), vmsAreaText);
            return sigungu != null ? sigungu.getId() : sido.getId();
        }

        return resolveViaRenameAlias(sidoCandidates, vmsAreaText);
    }

    private Long resolveViaRenameAlias(List<Region> sidoCandidates, String vmsAreaText) {
        for (Map.Entry<String, List<String>> alias : SIDO_RENAME_ALIASES.entrySet()) {
            if (!vmsAreaText.contains(alias.getKey())) {
                continue;
            }
            List<Region> oldNameSidos =
                    sidoCandidates.stream()
                            .filter(candidate -> alias.getValue().contains(candidate.getName()))
                            .toList();
            for (Region oldSido : oldNameSidos) {
                Region sigungu =
                        findMatch(regionRepository.findByParentId(oldSido.getId()), vmsAreaText);
                if (sigungu != null) {
                    return sigungu.getId();
                }
            }
            return oldNameSidos.size() == 1 ? oldNameSidos.get(0).getId() : null;
        }
        return null;
    }

    private Region findMatch(List<Region> candidates, String text) {
        return candidates.stream()
                .sorted(Comparator.comparingInt((Region r) -> r.getName().length()).reversed())
                .filter(candidate -> text.contains(candidate.getName()))
                .findFirst()
                .orElse(null);
    }
}
