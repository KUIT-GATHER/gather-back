package com.gather.gather.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 게시글에 반영된 이미지(최대 3장). {@code sortOrder}가 노출 순서이며, 목록 카드에서는 첫 번째(sortOrder=0) 이미지만 사용한다.
 * 도메인 결합을 피해 post를 연관관계 대신 ID로 보관한다.
 */
@Entity
@Getter
@Table(name = "post_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PostImage(Long postId, String objectKey, int sortOrder) {
        this.postId = postId;
        this.objectKey = objectKey;
        this.sortOrder = sortOrder;
    }

    public static PostImage create(Long postId, String objectKey, int sortOrder) {
        return new PostImage(postId, objectKey, sortOrder);
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
