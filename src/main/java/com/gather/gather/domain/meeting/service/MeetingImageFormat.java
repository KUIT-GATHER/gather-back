package com.gather.gather.domain.meeting.service;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.Arrays;

enum MeetingImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    MeetingImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    static MeetingImageFormat fromContentType(String contentType) {
        return Arrays.stream(values())
                .filter(format -> format.contentType.equals(contentType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MEETING_IMAGE_TYPE));
    }

    static MeetingImageFormat fromExtension(String extension) {
        return Arrays.stream(values())
                .filter(format -> format.extension.equals(extension))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_KEY));
    }

    String contentType() {
        return contentType;
    }

    String extension() {
        return extension;
    }
}