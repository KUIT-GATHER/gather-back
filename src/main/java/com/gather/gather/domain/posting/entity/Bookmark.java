package com.gather.gather.domain.posting.entity;

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

@Entity
@Getter
@Table(
        name = "bookmark",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_bookmark_user_posting",
                    columnNames = {"user_id", "posting_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "posting_id", nullable = false)
    private Long postingId;

    private Bookmark(Long userId, Long postingId) {
        this.userId = userId;
        this.postingId = postingId;
        this.createdAt = LocalDateTime.now();
    }

    public static Bookmark create(Long userId, Long postingId) {
        return new Bookmark(userId, postingId);
    }
}
