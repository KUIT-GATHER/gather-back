package com.gather.gather.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProfileImageUploadTest {

    @Test
    void createDeletionTask_createsAppliedCleanupCarrierWithCapturedNow() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 14, 25, 56, 123_456_000);

        ProfileImageUpload task =
                ProfileImageUpload.createDeletionTask(
                        1L, "__PROFILE_IMAGE_DELETION_TASK__/1/task-id", "profiles/1/old.jpg", now);

        assertThat(task.getUserId()).isEqualTo(1L);
        assertThat(task.getObjectKey()).isEqualTo("__PROFILE_IMAGE_DELETION_TASK__/1/task-id");
        assertThat(task.getStatus()).isEqualTo(ProfileImageUploadStatus.APPLIED);
        assertThat(task.getCreatedAt()).isEqualTo(now);
        assertThat(task.getAppliedAt()).isEqualTo(now);
        assertThat(task.getExpiresAt()).isEqualTo(now);
        assertThat(task.getPreviousObjectKey()).isEqualTo("profiles/1/old.jpg");
        assertThat(task.isPreviousObjectDeleted()).isFalse();
    }

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
