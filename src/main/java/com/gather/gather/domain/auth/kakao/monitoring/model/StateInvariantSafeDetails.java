package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import java.util.List;

public record StateInvariantSafeDetails(
        KakaoUnlinkStateInvariantType invariantType, int affectedCount, List<Long> taskIds)
        implements KakaoUnlinkIncidentSafeDetails {

    public StateInvariantSafeDetails {
        if (invariantType == null || affectedCount < 1) {
            throw new IllegalArgumentException("상태 invariant 관측 값이 올바르지 않습니다.");
        }
        taskIds = List.copyOf(taskIds == null ? List.of() : taskIds);
        if (taskIds.size() > MAX_SAMPLES
                || taskIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("상태 invariant task 표본이 올바르지 않습니다.");
        }
    }

    @Override
    public boolean supports(KakaoUnlinkAlertType alertType) {
        return alertType == KakaoUnlinkAlertType.STATE_INVARIANT_VIOLATION;
    }
}
