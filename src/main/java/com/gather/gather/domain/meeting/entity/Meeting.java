package com.gather.gather.domain.meeting.entity;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.posting.entity.PostingCategory;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "meeting_category", joinColumns = @JoinColumn(name = "meeting_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Set<PostingCategory> categories = new LinkedHashSet<>();

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

    @Column(name = "activity_start_at")
    private LocalDateTime activityStartAt;

    @Column(name = "activity_end_at")
    private LocalDateTime activityEndAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 완료 처리 시점(complete() 호출 시각). 뱃지 판정(연속 참여 월 계산)에 이 값을 사용한다 — updatedAt은 완료 이후 다른 변경(예: 소프트
     * 삭제)으로도 갱신될 수 있어 완료 시점 대용으로 쓸 수 없다.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private Meeting(
            String name,
            String description,
            Integer maxMember,
            LocalDateTime deadline,
            String memo,
            Set<PostingCategory> categories,
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
        this.categories = new LinkedHashSet<>(categories);
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
            Set<PostingCategory> categories,
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
                categories,
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
        return activityEndAt != null && activityEndAt.isBefore(now);
    }

    /** 활동 기간(activityStartAt~activityEndAt)이 설정된 모임인지 여부. 설정 안 된 자유 모임은 완료 처리에 날짜 게이트를 적용하지 않는다. */
    public boolean hasActivityPeriod() {
        return activityEndAt != null;
    }

    /**
     * 뱃지 판정(활동일 기준 연속 참여 월 계산)에 쓰이는 실질 활동 종료 시각. 활동 기간이 없는 자유 모임은 null을 반환하며, 이 경우 호출부는
     * completedAt(완료 처리 시각)으로 대체해야 한다.
     */
    public LocalDateTime getEffectiveActivityEnd() {
        return activityEndAt != null ? activityEndAt : activityStartAt;
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
        this.completedAt = LocalDateTime.now();
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }
}
