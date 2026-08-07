package com.gather.gather.domain.post.entity;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.post.enums.ReviewSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 모임 게시글(우리모임 게시판).
 *
 * <p>{@code deletedAt}으로 소프트 삭제한다. 좋아요/댓글은 별도 엔티티({@code PostLike}/{@code PostComment})가 원본을 보관하고,
 * 여기 {@code likeCount}·{@code commentCount}는 목록/상세에서 바로 노출하기 위한 집계 컬럼이다. 좋아요·댓글의 등록/삭제 시 서비스에서 함께
 * 증감한다.
 */
@Entity
@Getter
@Table(name = "post")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostType type;

    /** RECRUIT 유형일 때만 사용하는 모집 정원. */
    @Column(name = "recruit_capacity")
    private Integer recruitCapacity;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount;

    /** REVIEW 유형일 때만 사용. 이 후기가 근거로 삼은 활동(공고 참여/모집공고 참여)의 출처 구분. */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_source_type", length = 20)
    private ReviewSourceType reviewSourceType;

    /** REVIEW 유형일 때만 사용. reviewSourceType에 따라 postingId 또는 모집공고 postId를 가리킨다. */
    @Column(name = "review_source_id")
    private Long reviewSourceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Post(
            Meeting meeting,
            User user,
            String title,
            String content,
            PostType type,
            Integer recruitCapacity) {
        this.meeting = meeting;
        this.user = user;
        this.title = title;
        this.content = content;
        this.type = type;
        this.recruitCapacity = recruitCapacity;
        this.likeCount = 0;
        this.commentCount = 0;
    }

    public static Post create(
            Meeting meeting,
            User user,
            String title,
            String content,
            PostType type,
            Integer recruitCapacity) {
        return new Post(
                meeting,
                user,
                title,
                content,
                type,
                type == PostType.RECRUIT ? recruitCapacity : null);
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isAuthor(Long userId) {
        return this.user.getId().equals(userId);
    }

    /** 후기(REVIEW) 게시글을 특정 활동 참여 기록에 연결한다. */
    public void linkReviewSource(ReviewSourceType reviewSourceType, Long reviewSourceId) {
        this.reviewSourceType = reviewSourceType;
        this.reviewSourceId = reviewSourceId;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void increaseCommentCount() {
        this.commentCount++;
    }

    public void decreaseCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }
}
