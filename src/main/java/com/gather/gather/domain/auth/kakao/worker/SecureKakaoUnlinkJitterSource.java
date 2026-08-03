package com.gather.gather.domain.auth.kakao.worker;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureKakaoUnlinkJitterSource implements KakaoUnlinkJitterSource {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public long nextLong(long boundExclusive) {
        return secureRandom.nextLong(boundExclusive);
    }
}
