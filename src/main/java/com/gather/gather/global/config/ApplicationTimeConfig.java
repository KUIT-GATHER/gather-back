package com.gather.gather.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApplicationTimeConfig {

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
