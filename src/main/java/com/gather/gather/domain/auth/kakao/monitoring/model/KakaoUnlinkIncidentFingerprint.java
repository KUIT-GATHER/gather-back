package com.gather.gather.domain.auth.kakao.monitoring.model;

import java.util.regex.Pattern;

public record KakaoUnlinkIncidentFingerprint(String value) {

    public static final String SYNTHETIC_TEST_VALUE = "KAKAO_UNLINK:SYNTHETIC_TEST";
    private static final int MAX_LENGTH = 191;
    private static final Pattern SAFE_PATTERN = Pattern.compile("KAKAO_UNLINK:[A-Z0-9:_-]+");

    public KakaoUnlinkIncidentFingerprint {
        if (value == null || !SAFE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Kakao unlink incident fingerprint 형식이 올바르지 않습니다.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Kakao unlink incident fingerprint가 너무 깁니다.");
        }
    }

    public static KakaoUnlinkIncidentFingerprint syntheticTest() {
        return new KakaoUnlinkIncidentFingerprint(SYNTHETIC_TEST_VALUE);
    }
}
