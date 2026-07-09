package com.gather.gather.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "gather.cors")
public record CorsProperties(
        @DefaultValue({"https://gathernow.kr", "http://localhost:5173", "https://dev.gathernow.kr"})
                List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalStateException("gather.cors.allowed-origins는 하나 이상 필요합니다.");
        }
        if (allowedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank())) {
            throw new IllegalStateException("gather.cors.allowed-origins에 빈 origin이 포함될 수 없습니다.");
        }
    }
}
