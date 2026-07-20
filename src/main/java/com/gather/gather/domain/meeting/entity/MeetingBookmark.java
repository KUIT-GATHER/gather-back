package com.gather.gather.domain.meeting.entity;

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
        name = "meeting_bookmark",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_meeting_bookmark_user_meeting",
                    columnNames = {"user_id", "meeting_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    private MeetingBookmark(Long userId, Long meetingId) {
        this.userId = userId;
        this.meetingId = meetingId;
        this.createdAt = LocalDateTime.now();
    }

    public static MeetingBookmark create(Long userId, Long meetingId) {
        return new MeetingBookmark(userId, meetingId);
    }
}
