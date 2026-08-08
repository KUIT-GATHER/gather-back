package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;

public record KakaoUnlinkRecoveredDeliveryRequest(
        long incidentId, int occurrenceNo, KakaoUnlinkAlertChannel channel) {
    public KakaoUnlinkRecoveredDeliveryRequest {
        if (incidentId <= 0 || occurrenceNo < 1 || channel == null) {
            throw new IllegalArgumentException("RECOVERED delivery 요청 값이 올바르지 않습니다.");
        }
    }
}
