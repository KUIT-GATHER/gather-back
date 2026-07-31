package com.gather.gather.domain.auth.kakao.admin.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Kakao Admin API 전용 설정이다. */
@ConfigurationProperties(prefix = "kakao.admin")
public record KakaoAdminProperties(
        @DefaultValue("false") boolean enabled,
        String key,
        @DefaultValue("https://kapi.kakao.com") URI apiBaseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {

    public KakaoAdminProperties {
        if (enabled && (key == null || key.isBlank())) {
            throw new IllegalStateException(
                    "kakao.admin.enabled=true이면 KAKAO_ADMIN_KEY 설정이 필요합니다.");
        }
        requireHttpsBaseUrl(apiBaseUrl);
        requirePositive(connectTimeout, "kakao.admin.connect-timeout");
        requirePositive(readTimeout, "kakao.admin.read-timeout");
    }

    private static void requireHttpsBaseUrl(URI apiBaseUrl) {
        if (apiBaseUrl == null
                || !apiBaseUrl.isAbsolute()
                || apiBaseUrl.getHost() == null
                || !"https".equalsIgnoreCase(apiBaseUrl.getScheme())
                || apiBaseUrl.getUserInfo() != null
                || apiBaseUrl.getQuery() != null
                || apiBaseUrl.getFragment() != null
                || !hasEmptyOrRootPath(apiBaseUrl)) {
            throw new IllegalStateException("Invalid Kakao Admin API base URL");
        }
    }

    private static boolean hasEmptyOrRootPath(URI apiBaseUrl) {
        return apiBaseUrl.getPath().isEmpty() || "/".equals(apiBaseUrl.getPath());
    }

    private static void requirePositive(Duration timeout, String propertyName) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException(propertyName + "은 0보다 커야 합니다.");
        }
    }

    /** 기본 record toString()이 Admin key나 URI 민감 구성 요소를 노출하지 않도록 한다. */
    @Override
    public String toString() {
        return "KakaoAdminProperties[enabled="
                + enabled
                + ", key=****, apiBaseUrl="
                + apiBaseUrl.getScheme()
                + "://"
                + apiBaseUrl.getHost()
                + (apiBaseUrl.getPort() == -1 ? "" : ":" + apiBaseUrl.getPort())
                + ", connectTimeout="
                + connectTimeout
                + ", readTimeout="
                + readTimeout
                + "]";
    }
}
