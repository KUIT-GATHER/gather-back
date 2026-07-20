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
@Table(name = "meeting_search_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingSearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String keyword;

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    @Builder
    private MeetingSearchLog(String keyword) {
        this.keyword = keyword;
        this.searchedAt = LocalDateTime.now();
    }
}
