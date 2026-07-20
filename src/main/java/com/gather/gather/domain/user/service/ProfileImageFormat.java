package com.gather.gather.domain.user.service;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.Arrays;

enum ProfileImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ProfileImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    static ProfileImageFormat fromContentType(String contentType) {
        return Arrays.stream(values())
                .filter(format -> format.contentType.equals(contentType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE));
    }

    static ProfileImageFormat fromExtension(String extension) {
        return Arrays.stream(values())
                .filter(format -> format.extension.equals(extension))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY));
    }

    String contentType() {
        return contentType;
    }

    String extension() {
        return extension;
    }
}
