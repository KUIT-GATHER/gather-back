package com.gather.gather.domain.posting.crawler;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vms.crawl")
public record VmsCrawlProperties(
        String baseUrl,
        String userAgent,
        int maxPages,
        int requestDelayMs,
        int actDaysAhead,
        int maxDetailLookupsPerRun) {}
