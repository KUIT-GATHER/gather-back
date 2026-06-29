package com.gather.gather.domain.meeting.entity;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "meeting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting {
    private static final int INITIAL_MEMBER_COUNT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_member", nullable = false)
    private Integer maxMember;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Column(name = "current_member_count", nullable = false)
    private Integer currentMemberCount;

    @Column(name = "participation_condition", columnDefinition = "TEXT")
    private String participationCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingStatus status;

    @Column(name = "volunteer_posting_id")
    private Long volunteerPostingId;

    @Column(name = "activity_start_at", nullable = false)
    private LocalDateTime activityStartAt;

    @Column(name = "activity_end_at", nullable = false)
    private LocalDateTime activityEndAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Meeting(
            String name,
            String description,
            Integer maxMember,
            LocalDateTime deadline,
            String memo,
            Long categoryId,
            Long regionId,
            User host,
            String participationCondition,
            Long volunteerPostingId,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt) {
        this.name = name;
        this.description = description;
        this.maxMember = maxMember;
        this.deadline = deadline;
        this.memo = memo;
        this.categoryId = categoryId;
        this.regionId = regionId;
        this.host = host;
        this.currentMemberCount = INITIAL_MEMBER_COUNT;
        this.participationCondition = participationCondition;
        this.status = MeetingStatus.RECRUITING;
        this.volunteerPostingId = volunteerPostingId;
        this.activityStartAt = activityStartAt;
        this.activityEndAt = activityEndAt;
    }

    public static Meeting create(
            String name,
            String description,
            Integer maxMember,
            LocalDateTime deadline,
            String memo,
            Long categoryId,
            Long regionId,
            User host,
            String participationCondition,
            Long volunteerPostingId,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt) {
        return new Meeting(
                name,
                description,
                maxMember,
                deadline,
                memo,
                categoryId,
                regionId,
                host,
                participationCondition,
                volunteerPostingId,
                activityStartAt,
                activityEndAt);
    }
    public boolean isFull() {
        return currentMemberCount >= maxMember;
    }

    public boolean isDeadlinePassed(LocalDateTime now) {
        return deadline.isBefore(now);
    }

    public boolean isActivityEnded(LocalDateTime now) {
        return activityEndAt.isBefore(now);
    }

    public void increaseMemberCount() {
        this.currentMemberCount++;
        if (isFull()) {
            this.status = MeetingStatus.CLOSED;
        }
    }

    public void close() {
        this.status = MeetingStatus.CLOSED;
    }

    public void complete() {
        this.status = MeetingStatus.COMPLETED;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }
}
