package com.gather.gather.domain.auth.kakao.monitoring.model;

public record KakaoUnlinkAlertDeliveryResult(long deliveryId, int eventSequence, boolean created) {
    public KakaoUnlinkAlertDeliveryResult {
        if (deliveryId <= 0 || eventSequence < 1) {
            throw new IllegalArgumentException("delivery persistence 결과가 올바르지 않습니다.");
        }
    }
}
