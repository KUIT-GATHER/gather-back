package com.gather.gather.domain.posting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Table(
        name = "posting_participation",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_posting_participation_user_posting",
                    columnNames = {"user_id", "posting_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostingParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "posting_id", nullable = false)
    private Long postingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostingParticipationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PostingParticipation(Long userId, Long postingId, PostingParticipationStatus status) {
        this.userId = userId;
        this.postingId = postingId;
        this.status = status;
    }

    public static PostingParticipation create(Long userId, Long postingId) {
        return new PostingParticipation(userId, postingId, PostingParticipationStatus.APPLIED);
    }

    public void complete() {
        this.status = PostingParticipationStatus.COMPLETED;
    }
}
