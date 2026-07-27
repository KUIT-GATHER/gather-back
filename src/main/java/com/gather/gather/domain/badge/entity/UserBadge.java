package com.gather.gather.domain.badge.entity;

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
        name = "user_badge",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_user_badge_user_badge",
                    columnNames = {"user_id", "badge_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "badge_id", nullable = false)
    private Long badgeId;

    @Column(name = "achieved_at", nullable = false)
    private LocalDateTime achievedAt;

    private UserBadge(Long userId, Long badgeId, LocalDateTime achievedAt) {
        this.userId = userId;
        this.badgeId = badgeId;
        this.achievedAt = achievedAt;
    }

    public static UserBadge create(Long userId, Long badgeId) {
        return new UserBadge(userId, badgeId, LocalDateTime.now());
    }
}
