package com.gather.gather.global.util;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 로그인 사용자의 활동지역으로 봉사공고를 완전 필터링하기 위한 regionId 목록 조회(봉사공고 추천 전용).
 *
 * <p>반환값이 {@code null}이면 "필터 없음"을 의미한다 — 비로그인, 인증된 userId에 실제 User row가 없는 경우(탈퇴 직후 등), 활동지역을 설정하지
 * 않은 회원은 모두 이 경우에 해당하며 기존과 동일하게 전체 지역이 노출된다. {@code PostingRepositoryCustom#search}는 빈 리스트를 "0건"으로
 * 취급하므로 필터가 없을 때는 반드시 {@code null}을 반환해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityRegionResolver {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;

    public List<Long> resolveFilterRegionIds(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository
                .findById(userId)
                .map(this::resolveFromUser)
                .orElseGet(
                        () -> {
                            log.warn(
                                    "인증된 userId={}에 해당하는 회원 정보를 찾을 수 없어 활동지역 필터를 적용하지 않습니다.",
                                    userId);
                            return null;
                        });
    }

    private List<Long> resolveFromUser(User user) {
        Region activityRegion = user.getActivityRegion();
        if (activityRegion == null) {
            return null;
        }
        return regionRepository.findIdsIncludingChildren(activityRegion.getId());
    }
}
