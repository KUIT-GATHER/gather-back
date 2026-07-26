package com.gather.gather.domain.meeting.service;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class MeetingImageContentValidator {

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    public void validate(MeetingImageFormat format, byte[] content) {
        boolean valid =
                switch (format) {
                    case JPEG -> startsWith(content, JPEG_SIGNATURE);
                    case PNG -> startsWith(content, PNG_SIGNATURE);
                    case WEBP -> isWebp(content);
                };
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_CONTENT);
        }
    }

    private boolean isWebp(byte[] content) {
        if (content == null
                || content.length < 16
                || !matchesAt(content, RIFF, 0)
                || !matchesAt(content, WEBP, 8)) {
            return false;
        }
        long declaredSize =
                Integer.toUnsignedLong(
                        (content[4] & 0xFF)
                                | ((content[5] & 0xFF) << 8)
                                | ((content[6] & 0xFF) << 16)
                                | ((content[7] & 0xFF) << 24));
        String chunkType = new String(content, 12, 4, StandardCharsets.US_ASCII);
        return declaredSize + 8 == content.length
                && (chunkType.equals("VP8 ")
                        || chunkType.equals("VP8L")
                        || chunkType.equals("VP8X"));
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        return content != null
                && content.length >= signature.length
                && Arrays.equals(Arrays.copyOfRange(content, 0, signature.length), signature);
    }

    private boolean matchesAt(byte[] content, byte[] expected, int offset) {
        return Arrays.equals(
                Arrays.copyOfRange(content, offset, offset + expected.length), expected);
    }
}
