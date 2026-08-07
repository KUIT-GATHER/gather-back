package com.gather.gather.domain.auth.kakao.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "gather.kakao.unlink-canary")
public record KakaoUnlinkCanaryCommandProperties(
        @DefaultValue("false") boolean enabled, String taskId) {}
