package com.gather.gather.domain.user.service;

import com.gather.gather.global.infra.s3.S3Properties;
import org.springframework.stereotype.Component;

@Component
public class ProfileImageUrlResolver {

    private final String publicBaseUrl;

    public ProfileImageUrlResolver(S3Properties properties) {
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
