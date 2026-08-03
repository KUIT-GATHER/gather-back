package com.gather.gather.domain.post.enums;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.Arrays;

/** 게시글 이미지 허용 포맷(프로필·모임 이미지와 동일하게 JPEG/PNG/WebP). */
public enum PostImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    PostImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public static PostImageFormat fromContentType(String contentType) {
        return Arrays.stream(values())
                .filter(format -> format.contentType.equalsIgnoreCase(contentType))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_POST_IMAGE_TYPE));
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
