package com.gather.gather.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProfileImageUploadTest {

    @Test
    void apply_rejectsAlreadyAppliedUpload() {
        ProfileImageUpload upload =
                ProfileImageUpload.create(
                        1L,
                        "profiles/1/550e8400-e29b-41d4-a716-446655440000.jpg",
                        "image/jpeg",
                        1024,
                        LocalDateTime.now().plusMinutes(5),
                        LocalDateTime.now());
        upload.apply(null, LocalDateTime.now());

        assertThatThrownBy(() -> upload.apply(null, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PROFILE_IMAGE_KEY);
    }
}
