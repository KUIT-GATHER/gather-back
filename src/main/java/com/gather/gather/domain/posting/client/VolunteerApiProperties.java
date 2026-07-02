package com.gather.gather.domain.posting.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "volunteer-api")
public record VolunteerApiProperties(String baseUrl, String serviceKey) {}
