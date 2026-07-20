package com.gather.gather.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfileImageContentValidatorTest {

    private final ProfileImageContentValidator validator = new ProfileImageContentValidator();

    @Test
    @DisplayName("JPEG, PNG, WebP의 실제 파일 시그니처를 허용한다")
    void validate_acceptsSupportedImageSignatures() {
        assertThatCode(() -> validator.validate(ProfileImageFormat.JPEG, jpeg()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(ProfileImageFormat.PNG, png()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(ProfileImageFormat.WEBP, webp()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Content-Type만 이미지인 임의 바이너리는 거부한다")
    void validate_rejectsDisguisedBinary() {
        assertThatThrownBy(() -> validator.validate(ProfileImageFormat.JPEG, new byte[32]))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PROFILE_IMAGE_CONTENT);
    }

    @Test
    @DisplayName("WebP RIFF 길이와 chunk type이 일치하지 않으면 거부한다")
    void validate_rejectsMalformedWebp() {
        byte[] malformed = webp();
        malformed[4] = 7;

        assertThatThrownBy(() -> validator.validate(ProfileImageFormat.WEBP, malformed))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PROFILE_IMAGE_CONTENT);
    }

    private byte[] jpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }

    private byte[] png() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private byte[] webp() {
        byte[] content = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, content, 0, 4);
        content[4] = 8;
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, content, 8, 4);
        System.arraycopy("VP8X".getBytes(StandardCharsets.US_ASCII), 0, content, 12, 4);
        return content;
    }
}
