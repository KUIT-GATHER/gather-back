package com.gather.gather.domain.auth.octomo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "octomo")
public record OctomoProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://api.octoverse.kr") String baseUrl,
        @DefaultValue("16663538") String receiverNumber) {

    public OctomoProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("octomo.base-url은 비어 있을 수 없습니다.");
        }
        if (receiverNumber == null || !receiverNumber.matches("^[0-9]{8,15}$")) {
            throw new IllegalStateException("octomo.receiver-number는 하이픈 없는 숫자여야 합니다.");
        }
    }

    @Override
    public String toString() {
        return "OctomoProperties[apiKey=****, baseUrl="
                + baseUrl
                + ", receiverNumber="
                + receiverNumber
                + "]";
    }
}
