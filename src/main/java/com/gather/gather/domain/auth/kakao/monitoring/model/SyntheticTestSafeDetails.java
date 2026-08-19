package com.gather.gather.domain.auth.kakao.monitoring.model;

import com.gather.gather.domain.auth.entity.KakaoUnlinkAlertType;

public record SyntheticTestSafeDetails() implements KakaoUnlinkIncidentSafeDetails {

    @Override
    public boolean supports(KakaoUnlinkAlertType alertType) {
        return alertType == KakaoUnlinkAlertType.SYNTHETIC_TEST;
    }
}
