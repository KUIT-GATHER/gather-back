package com.gather.gather.domain.meeting.entity;

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

@Entity
@Getter
@Table(name = "meeting_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "object_key", nullable = false, unique = true, length = 255)
    private String objectKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private MeetingImage(Long meetingId, String objectKey, int sortOrder) {
        this.meetingId = meetingId;
        this.objectKey = objectKey;
        this.sortOrder = sortOrder;
    }

    public static MeetingImage create(Long meetingId, String objectKey, int sortOrder) {
        return new MeetingImage(meetingId, objectKey, sortOrder);
    }
}