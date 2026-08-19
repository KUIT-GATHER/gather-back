package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;
import java.util.List;

public record DeadTaskSummarySafeDetails(int affectedCount, List<DeadTaskSample> samples)
        implements KakaoUnlinkIncidentSafeDetails {

    public DeadTaskSummarySafeDetails {
        if (affectedCount < 1) {
            throw new IllegalArgumentException("DEAD task 집계 건수는 1 이상이어야 합니다.");
        }
        samples = List.copyOf(samples == null ? List.of() : samples);
        if (samples.size() > MAX_SAMPLES) {
            throw new IllegalArgumentException("DEAD task 표본은 최대 5개까지 허용됩니다.");
        }
    }

    @Override
    public boolean supports(KakaoUnlinkAlertType alertType) {
        return alertType == KakaoUnlinkAlertType.DEAD_TASK_SUMMARY;
    }
}
