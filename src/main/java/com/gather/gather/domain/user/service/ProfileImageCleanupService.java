package com.gather.gather.domain.user.service;

import com.gather.gather.domain.user.entity.ProfileImageUpload;
import com.gather.gather.domain.user.entity.ProfileImageUploadStatus;
import com.gather.gather.domain.user.repository.ProfileImageUploadRepository;
import com.gather.gather.global.infra.s3.ObjectStorage;
import com.gather.gather.global.infra.s3.S3Properties;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageCleanupService {

    private final ProfileImageUploadRepository profileImageUploadRepository;
    private final ObjectStorage objectStorage;
    private final S3Properties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deletePreviousObject(Long uploadId) {
        ProfileImageUpload upload =
                profileImageUploadRepository.findByIdForUpdate(uploadId).orElse(null);
        if (upload == null
                || upload.isPending()
                || upload.isPreviousObjectDeleted()
                || upload.getPreviousObjectKey() == null) {
            return;
        }
        objectStorage.delete(upload.getPreviousObjectKey());
        upload.markPreviousObjectDeleted();
    }

    @Transactional
    public int cleanupExpiredUploads() {
        List<ProfileImageUpload> expiredUploads =
                profileImageUploadRepository.findExpiredForUpdate(
                        ProfileImageUploadStatus.PENDING,
                        LocalDateTime.now(),
                        PageRequest.of(0, properties.cleanupBatchSize()));
        int cleanedCount = 0;
        for (ProfileImageUpload upload : expiredUploads) {
            try {
                objectStorage.delete(upload.getObjectKey());
                profileImageUploadRepository.delete(upload);
                cleanedCount++;
            } catch (RuntimeException exception) {
                log.warn(
                        "만료된 프로필 이미지 업로드 객체 삭제에 실패했습니다: uploadId={}, cause={}",
                        upload.getId(),
                        exception.getClass().getSimpleName());
            }
        }
        return cleanedCount;
    }

    @Transactional
    public int retryPreviousObjectDeletions() {
        LocalDateTime safeBefore =
                LocalDateTime.now().minusSeconds(properties.presignedUrlExpirationSeconds());
        List<ProfileImageUpload> uploads =
                profileImageUploadRepository.findPreviousDeletionPendingForUpdate(
                        ProfileImageUploadStatus.APPLIED,
                        safeBefore,
                        PageRequest.of(0, properties.cleanupBatchSize()));
        int cleanedCount = 0;
        for (ProfileImageUpload upload : uploads) {
            try {
                objectStorage.delete(upload.getPreviousObjectKey());
                if (upload.getAppliedAt().isAfter(safeBefore)) {
                    upload.markPreviousObjectDeleted();
                } else {
                    profileImageUploadRepository.delete(upload);
                }
                cleanedCount++;
            } catch (RuntimeException exception) {
                log.warn(
                        "기존 프로필 이미지 객체 재삭제에 실패했습니다: uploadId={}, cause={}",
                        upload.getId(),
                        exception.getClass().getSimpleName());
            }
        }
        return cleanedCount;
    }
}
