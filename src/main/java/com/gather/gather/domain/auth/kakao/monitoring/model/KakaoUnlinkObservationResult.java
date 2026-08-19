package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkIncidentTransition;
import java.util.List;

public record KakaoUnlinkObservationResult(
        KakaoUnlinkIncidentSnapshot snapshot,
        KakaoUnlinkIncidentTransition transition,
        List<KakaoUnlinkAlertDeliveryResult> deliveryResults) {

    public KakaoUnlinkObservationResult {
        if (snapshot == null || transition == null) {
            throw new IllegalArgumentException("incident observation 결과는 필수입니다.");
        }
        deliveryResults = List.copyOf(deliveryResults == null ? List.of() : deliveryResults);
    }
}
