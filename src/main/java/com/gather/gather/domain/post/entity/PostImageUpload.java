package com.gather.gather.domain.post.entity;

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

/**
 * 게시글 이미지 presigned 업로드 세션.
 *
 * <p>게시글은 작성 화면에서 먼저 이미지를 올리고 등록 시 objectKey를 넘기는 흐름이라, 업로드 세션은 특정 게시글이 아니라 사용자(userId) 단위로
 * 발급한다({@code ProfileImageUpload}와 동일한 형태). 게시글 등록/수정 시 {@link #apply}로 APPLIED 전환하며 {@code
 * post_image}에 반영한다.
 */
@Entity
@Getter
@Table(name = "post_image_upload")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImageUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "expected_size", nullable = false)
    private long expectedSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostImageUploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    private PostImageUpload(
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
        this.status = PostImageUploadStatus.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public static PostImageUpload create(
            Long userId,
            String objectKey,
            String contentType,
            long expectedSize,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        return new PostImageUpload(
                userId, objectKey, contentType, expectedSize, expiresAt, createdAt);
    }

    public boolean isPending() {
        return status == PostImageUploadStatus.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void apply(LocalDateTime appliedAt) {
        this.status = PostImageUploadStatus.APPLIED;
        this.appliedAt = appliedAt;
    }
}
