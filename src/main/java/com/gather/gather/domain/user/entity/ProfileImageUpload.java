package com.gather.gather.domain.user.entity;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "profile_image_upload")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImageUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "object_key", nullable = false, unique = true, length = 255)
    private String objectKey;

    @Column(nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private long expectedSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileImageUploadStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;

    @Column(length = 255)
    private String previousObjectKey;

    @Column(nullable = false)
    private boolean previousObjectDeleted;

    private ProfileImageUpload(
            Long userId,
            String objectKey,
            String contentType,
            long expectedSize,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.userId = userId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.expectedSize = expectedSize;
        this.status = ProfileImageUploadStatus.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.previousObjectDeleted = true;
    }

    public static ProfileImageUpload create(
            Long userId,
            String objectKey,
            String contentType,
            long expectedSize,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        return new ProfileImageUpload(
                userId, objectKey, contentType, expectedSize, expiresAt, createdAt);
    }

    public static ProfileImageUpload createDeletionTask(
            Long userId, String trackingKey, String deletionTarget, LocalDateTime createdAt) {
        ProfileImageUpload upload =
                new ProfileImageUpload(
                        userId, trackingKey, "application/octet-stream", 0L, createdAt, createdAt);
        upload.status = ProfileImageUploadStatus.APPLIED;
        upload.appliedAt = createdAt;
        upload.previousObjectKey = deletionTarget;
        upload.previousObjectDeleted = false;
        return upload;
    }

    public boolean isPending() {
        return status == ProfileImageUploadStatus.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void validatePendingSession(LocalDateTime now, String expectedContentType) {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
        }
        if (isExpired(now)) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_UPLOAD_EXPIRED);
        }
        if (!contentType.equals(expectedContentType)) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
        }
    }

    public void apply(String previousObjectKey, LocalDateTime appliedAt) {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.INVALID_PROFILE_IMAGE_KEY);
        }
        this.status = ProfileImageUploadStatus.APPLIED;
        this.appliedAt = appliedAt;
        this.previousObjectKey = previousObjectKey;
        this.previousObjectDeleted = previousObjectKey == null;
    }

    public void markPreviousObjectDeleted() {
        this.previousObjectDeleted = true;
    }
}
