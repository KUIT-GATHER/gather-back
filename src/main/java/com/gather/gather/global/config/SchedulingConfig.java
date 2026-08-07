package com.gather.gather.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!kakao-unlink-resume & !kakao-unlink-canary")
@ConditionalOnProperty(
        name = "gather.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SchedulingConfig {}
