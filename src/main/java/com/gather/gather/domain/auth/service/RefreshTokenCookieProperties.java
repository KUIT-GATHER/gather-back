package com.gather.gather.domain.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "gather.auth.refresh-cookie")
public record RefreshTokenCookieProperties(
        @DefaultValue("gather_refresh_token") String name,
        @DefaultValue("false") boolean secure,
        @DefaultValue("Lax") String sameSite,
        @DefaultValue("/api/v1/auth") String path,
        @DefaultValue("1209600") long maxAgeSeconds) {

    public RefreshTokenCookieProperties {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("gather.auth.refresh-cookie.name은 비어 있을 수 없습니다.");
        }
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalStateException("gather.auth.refresh-cookie.same-site는 비어 있을 수 없습니다.");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("gather.auth.refresh-cookie.path는 비어 있을 수 없습니다.");
        }
        if (maxAgeSeconds <= 0) {
            throw new IllegalStateException(
                    "gather.auth.refresh-cookie.max-age-seconds는 1 이상이어야 합니다.");
        }
    }
}
