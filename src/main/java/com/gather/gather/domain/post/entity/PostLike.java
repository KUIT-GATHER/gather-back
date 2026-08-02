package com.gather.gather.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 게시글 좋아요.
 *
 * <p>취소는 물리 삭제로 처리하고 {@code (post_id, user_id)} UNIQUE로 중복 좋아요를 막는다. 좋아요 총계는 {@code
 * post.likeCount} 집계 컬럼으로 노출하므로, 이 엔티티는 "누가 눌렀는지"만 보관한다. 도메인 결합을 피하려고 post/user를 연관관계 대신 ID로만
 * 보관한다({@code PostingParticipation}과 동일 컨벤션).
 */
@Entity
@Getter
@Table(
        name = "post_like",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_post_like_post_user",
                    columnNames = {"post_id", "user_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PostLike(Long postId, Long userId) {
        this.postId = postId;
        this.userId = userId;
    }

    public static PostLike create(Long postId, Long userId) {
        return new PostLike(postId, userId);
    }
}
