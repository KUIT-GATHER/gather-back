package com.gather.gather.domain.auth.kakao.worker;

public interface KakaoUnlinkJitterSource {

    long nextLong(long boundExclusive);
}
