package com.gather.gather.domain.meeting.entity;

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
@Table(name = "meeting_image_upload")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingImageUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "issuer_user_id", nullable = false)
    private Long issuerUserId;

    @Column(name = "object_key", nullable = false, unique = true, length = 255)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "expected_size", nullable = false)
    private long expectedSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingImageUploadStatus status;

    // SUPERSEDED 로 바뀔 때 false 로 내려가고, 정리 배치가 S3 삭제 후 행을 제거한다.
    @Column(name = "object_deleted", nullable = false)
    private boolean objectDeleted;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    private MeetingImageUpload(
            Long meetingId,
            Long issuerUserId,
            String objectKey,
            String contentType,
            long expectedSize,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.meetingId = meetingId;
        this.issuerUserId = issuerUserId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.expectedSize = expectedSize;
        this.status = MeetingImageUploadStatus.PENDING;
        this.objectDeleted = true; // 발급/반영 상태에서는 삭제 대상이 아니다.
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static MeetingImageUpload create(
            Long meetingId,
            Long issuerUserId,
            String objectKey,
            String contentType,
            long expectedSize,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        return new MeetingImageUpload(
                meetingId, issuerUserId, objectKey, contentType, expectedSize, expiresAt, createdAt);
    }

    public boolean isPending() {
        return status == MeetingImageUploadStatus.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void validatePendingSession(LocalDateTime now, String expectedContentType) {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_KEY);
        }
        if (isExpired(now)) {
            throw new BusinessException(ErrorCode.MEETING_IMAGE_UPLOAD_EXPIRED);
        }
        if (!contentType.equals(expectedContentType)) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_KEY);
        }
    }

    public void apply(LocalDateTime appliedAt) {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.INVALID_MEETING_IMAGE_KEY);
        }
        this.status = MeetingImageUploadStatus.APPLIED;
        this.appliedAt = appliedAt;
    }

    /** 교체로 더 이상 쓰이지 않는 객체. 정리 배치가 S3에서 삭제한다. */
    public void supersede() {
        this.status = MeetingImageUploadStatus.SUPERSEDED;
        this.objectDeleted = false;
    }

    public void markObjectDeleted() {
        this.objectDeleted = true;
    }
}