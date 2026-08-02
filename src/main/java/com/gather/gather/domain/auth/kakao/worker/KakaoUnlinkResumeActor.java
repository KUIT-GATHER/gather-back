package com.gather.gather.domain.auth.kakao.worker;

import java.util.regex.Pattern;

final class KakaoUnlinkResumeActor {

    static final int MAX_LENGTH = 64;
    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9._@-]+");

    private KakaoUnlinkResumeActor() {}

    static String normalize(String rawActor) {
        if (rawActor == null) {
            throw new IllegalArgumentException("resume actor는 필수입니다.");
        }
        String actor = rawActor.trim();
        if (actor.isEmpty() || actor.length() > MAX_LENGTH || !PATTERN.matcher(actor).matches()) {
            throw new IllegalArgumentException("resume actor 형식이 올바르지 않습니다.");
        }
        return actor;
    }
}
