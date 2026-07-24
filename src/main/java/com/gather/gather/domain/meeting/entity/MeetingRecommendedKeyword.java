package com.gather.gather.domain.meeting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "meeting_recommended_keyword")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingRecommendedKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String keyword;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private MeetingRecommendedKeyword(String keyword, Integer score) {
        this.keyword = keyword;
        this.score = score;
        this.updatedAt = LocalDateTime.now();
    }
}
