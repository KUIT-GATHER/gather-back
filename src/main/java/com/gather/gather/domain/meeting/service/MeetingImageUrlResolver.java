package com.gather.gather.domain.meeting.service;

import com.gather.gather.global.infra.s3.S3Properties;
import org.springframework.stereotype.Component;

@Component
public class MeetingImageUrlResolver {

    private final String publicBaseUrl;

    public MeetingImageUrlResolver(S3Properties properties) {
        this.publicBaseUrl = stripTrailingSlash(properties.publicBaseUrl());
    }

    public String resolve(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        return publicBaseUrl + "/" + objectKey;
    }

    private String stripTrailingSlash(String url) {
        int endIndex = url.length();
        while (endIndex > 0 && url.charAt(endIndex - 1) == '/') {
            endIndex--;
        }
        return url.substring(0, endIndex);
    }
}
