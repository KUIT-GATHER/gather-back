package com.gather.gather.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.user.entity.ProfileImageUpload;
import com.gather.gather.domain.user.entity.ProfileImageUploadStatus;
import com.gather.gather.domain.user.repository.ProfileImageUploadRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.infra.s3.ObjectStorage;
import com.gather.gather.global.infra.s3.S3Properties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileImageCleanupServiceTest {

    private static final Long USER_ID = 15L;
    private static final Long UPLOAD_ID = 99L;
    private static final String NEW_KEY = "profiles/15/550e8400-e29b-41d4-a716-446655440000.jpg";
    private static final String PREVIOUS_KEY =
            "profiles/15/00000000-0000-0000-0000-000000000000.jpg";
    private static final S3Properties PROPERTIES =
            new S3Properties(
                    "ap-northeast-2",
                    "test-profile-images",
                    "https://test-profile-images.example",
                    300,
                    5L * 1024 * 1024,
                    "profiles",
                    3,
                    100,
                    3_600_000,
                    false);

    @Mock private ProfileImageUploadRepository profileImageUploadRepository;
    @Mock private ObjectStorage objectStorage;

    private ProfileImageCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService =
                new ProfileImageCleanupService(
                        profileImageUploadRepository, objectStorage, PROPERTIES);
    }

    @Test
    @DisplayName("커밋 후 기존 객체 삭제가 성공하면 영속 작업을 완료 처리한다")
    void deletePreviousObject_marksDeletionCompleted() {
        ProfileImageUpload upload = appliedUpload();
        when(profileImageUploadRepository.findByIdForUpdate(UPLOAD_ID))
                .thenReturn(Optional.of(upload));

        cleanupService.deletePreviousObject(UPLOAD_ID);

        verify(objectStorage).delete(PREVIOUS_KEY);
        assertThat(upload.isPreviousObjectDeleted()).isTrue();
        verify(profileImageUploadRepository, never()).delete(upload);
    }

    @Test
    @DisplayName("커밋 후 기존 객체 삭제가 실패하면 작업을 미완료 상태로 유지한다")
    void deletePreviousObject_keepsRetryState_whenStorageFails() {
        ProfileImageUpload upload = appliedUpload();
        when(profileImageUploadRepository.findByIdForUpdate(UPLOAD_ID))
                .thenReturn(Optional.of(upload));
        whenStorageDeleteFails(PREVIOUS_KEY);

        assertThatThrownBy(() -> cleanupService.deletePreviousObject(UPLOAD_ID))
                .isInstanceOf(BusinessException.class);

        assertThat(upload.isPreviousObjectDeleted()).isFalse();
    }

    @Test
    @DisplayName("만료된 미반영 업로드 객체를 삭제한 후 발급 기록도 제거한다")
    void cleanupExpiredUploads_deletesObjectAndRecord() {
        ProfileImageUpload upload = pendingUpload(LocalDateTime.now().minusMinutes(1));
        when(profileImageUploadRepository.findExpiredForUpdate(
                        eq(ProfileImageUploadStatus.PENDING),
                        any(LocalDateTime.class),
                        any(Pageable.class)))
                .thenReturn(List.of(upload));

        int count = cleanupService.cleanupExpiredUploads();

        assertThat(count).isEqualTo(1);
        verify(objectStorage).delete(NEW_KEY);
        verify(profileImageUploadRepository).delete(upload);
    }

    @Test
    @DisplayName("만료 객체 삭제가 실패하면 기록을 남겨 다음 배치에서 재시도한다")
    void cleanupExpiredUploads_keepsRecord_whenStorageFails() {
        ProfileImageUpload upload = pendingUpload(LocalDateTime.now().minusMinutes(1));
        when(profileImageUploadRepository.findExpiredForUpdate(
                        eq(ProfileImageUploadStatus.PENDING),
                        any(LocalDateTime.class),
                        any(Pageable.class)))
                .thenReturn(List.of(upload));
        whenStorageDeleteFails(NEW_KEY);

        int count = cleanupService.cleanupExpiredUploads();

        assertThat(count).isZero();
        verify(profileImageUploadRepository, never()).delete(upload);
    }

    @Test
    @DisplayName("미완료 기존 객체 삭제 작업을 배치가 다시 시도한다")
    void retryPreviousObjectDeletions_retriesPersistentTask() {
        ProfileImageUpload upload = appliedUpload();
        when(profileImageUploadRepository.findPreviousDeletionPendingForUpdate(
                        eq(ProfileImageUploadStatus.APPLIED),
                        any(LocalDateTime.class),
                        any(Pageable.class)))
                .thenReturn(List.of(upload));

        int count = cleanupService.retryPreviousObjectDeletions();

        assertThat(count).isEqualTo(1);
        verify(objectStorage).delete(PREVIOUS_KEY);
        assertThat(upload.isPreviousObjectDeleted()).isTrue();
        verify(profileImageUploadRepository, never()).delete(upload);
    }

    @Test
    @DisplayName("Presigned URL 최대 유효기간이 지나면 기존 key를 다시 삭제하고 추적 기록을 정리한다")
    void retryPreviousObjectDeletions_performsFinalSweepAfterUrlExpiration() {
        ProfileImageUpload upload = pendingUpload(LocalDateTime.now().minusMinutes(10));
        upload.apply(PREVIOUS_KEY, LocalDateTime.now().minusMinutes(10));
        upload.markPreviousObjectDeleted();
        when(profileImageUploadRepository.findPreviousDeletionPendingForUpdate(
                        eq(ProfileImageUploadStatus.APPLIED),
                        any(LocalDateTime.class),
                        any(Pageable.class)))
                .thenReturn(List.of(upload));

        int count = cleanupService.retryPreviousObjectDeletions();

        assertThat(count).isEqualTo(1);
        verify(objectStorage).delete(PREVIOUS_KEY);
        verify(profileImageUploadRepository).delete(upload);
    }

    private ProfileImageUpload pendingUpload(LocalDateTime expiresAt) {
        ProfileImageUpload upload =
                ProfileImageUpload.create(
                        USER_ID,
                        NEW_KEY,
                        "image/jpeg",
                        1024,
                        expiresAt,
                        LocalDateTime.now().minusMinutes(5));
        ReflectionTestUtils.setField(upload, "id", UPLOAD_ID);
        return upload;
    }

    private ProfileImageUpload appliedUpload() {
        ProfileImageUpload upload = pendingUpload(LocalDateTime.now().plusMinutes(5));
        upload.apply(PREVIOUS_KEY, LocalDateTime.now());
        return upload;
    }

    private void whenStorageDeleteFails(String objectKey) {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.S3_OPERATION_FAILED))
                .when(objectStorage)
                .delete(objectKey);
    }
}
