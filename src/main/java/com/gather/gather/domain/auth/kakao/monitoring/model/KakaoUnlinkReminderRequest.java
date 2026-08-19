package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertChannel;
import java.time.LocalDateTime;

public record KakaoUnlinkReminderRequest(
        KakaoUnlinkMonitorLease lease,
        long incidentId,
        KakaoUnlinkAlertChannel channel,
        LocalDateTime nextReminderAt) {
    public KakaoUnlinkReminderRequest {
        if (lease == null || incidentId <= 0 || channel == null) {
            throw new IllegalArgumentException("reminder persistence 요청 값이 올바르지 않습니다.");
        }
    }
}
