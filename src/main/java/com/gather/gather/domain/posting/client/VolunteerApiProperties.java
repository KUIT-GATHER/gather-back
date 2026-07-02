package com.gather.gather.domain.posting.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "volunteer-api")
public record VolunteerApiProperties(String baseUrl, String serviceKey) {

    /** 기본 record toString()이 serviceKey를 그대로 노출하는 것을 막기 위한 마스킹 오버라이드. */
    @Override
    public String toString() {
        return "VolunteerApiProperties[baseUrl=" + baseUrl + ", serviceKey=****]";
    }
}
