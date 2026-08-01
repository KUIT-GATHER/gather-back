package com.gather.gather.domain.auth.kakao.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KakaoTimeConfig {

    @Bean
    public Clock kakaoClock() {
        return Clock.systemDefaultZone();
    }
}
