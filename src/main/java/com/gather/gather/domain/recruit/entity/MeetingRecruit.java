package com.gather.gather.domain.recruit.entity;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 모임 내부 모집공고(RECRUIT 게시글)의 확장 정보. {@code post_id}로 RECRUIT 유형 {@code Post}와 1:1 대응한다. 도메인 결합을 피해
 * post/region을 연관관계 대신 ID로 보관한다.
 *
 * <p>여러 날짜에 걸친 활동을 허용하기 위해 활동 기간을 {@code activityStartAt}~{@code activityEndAt}(날짜+시각)로 통일해서
 * 관리한다. 신청은 {@code applyDeadlineAt} 시각까지 가능하고, {@code confirmationStatus}가 {@code CONFIRMED}로 바뀌면(팀장이
 * 신청자를 확정하거나 마감 후 자동 확정되면) 이후로는 신규 신청·취소가 불가능하다.
 */
@Entity
@Getter
@Table(name = "meeting_recruit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingRecruit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "region_id")
    private Long regionId;

    @Column(nullable = false, length = 255)
    private String place;

    @Column(name = "activity_start_at", nullable = false)
    private LocalDateTime activityStartAt;

    @Column(name = "activity_end_at", nullable = false)
    private LocalDateTime activityEndAt;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "time_recognized", nullable = false)
    private boolean timeRecognized;

    @Column(name = "recognized_minutes")
    private Integer recognizedMinutes;

    @Column(name = "apply_deadline_at", nullable = false)
    private LocalDateTime applyDeadlineAt;

    @Column(nullable = false)
    private boolean external;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_status", nullable = false, length = 20)
    private RecruitConfirmationStatus confirmationStatus;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "meeting_recruit_category",
            joinColumns = @JoinColumn(name = "recruit_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Set<PostingCategory> categories = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private MeetingRecruit(
            Long postId,
            Long regionId,
            String place,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt,
            int maxParticipants,
            boolean timeRecognized,
            Integer recognizedMinutes,
            LocalDateTime applyDeadlineAt,
            boolean external,
            Set<PostingCategory> categories) {
        this.postId = postId;
        this.regionId = regionId;
        this.place = place;
        this.activityStartAt = activityStartAt;
        this.activityEndAt = activityEndAt;
        this.maxParticipants = maxParticipants;
        this.timeRecognized = timeRecognized;
        this.recognizedMinutes = recognizedMinutes;
        this.applyDeadlineAt = applyDeadlineAt;
        this.external = external;
        this.confirmationStatus = RecruitConfirmationStatus.UNCONFIRMED;
        this.categories = new LinkedHashSet<>(categories);
    }

    public static MeetingRecruit create(
            Long postId,
            Long regionId,
            String place,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt,
            int maxParticipants,
            boolean timeRecognized,
            Integer recognizedMinutes,
            LocalDateTime applyDeadlineAt,
            boolean external,
            Set<PostingCategory> categories) {
        return new MeetingRecruit(
                postId,
                regionId,
                place,
                activityStartAt,
                activityEndAt,
                maxParticipants,
                timeRecognized,
                recognizedMinutes,
                applyDeadlineAt,
                external,
                categories);
    }

    /** 신청 가능한 시각인지: 신청 마감 시각까지(포함) 신청 가능하다. 확정 여부는 서비스에서 별도로 확인한다. */
    public boolean isApplicationOpen(LocalDateTime now) {
        return !now.isAfter(applyDeadlineAt);
    }

    /** 활동이 종료됐는지. */
    public boolean isActivityEnded(LocalDateTime now) {
        return activityEndAt.isBefore(now);
    }

    /** 팀장이 신청자를 확정하거나(수동) 마감 후 자동 확정될 때 호출한다. */
    public void confirm(LocalDateTime now) {
        this.confirmationStatus = RecruitConfirmationStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    /** 모집공고 확장 필드 수정. 제목·내용은 연결된 Post에서 별도로 갱신한다. */
    public void update(
            Long regionId,
            String place,
            LocalDateTime activityStartAt,
            LocalDateTime activityEndAt,
            int maxParticipants,
            boolean timeRecognized,
            Integer recognizedMinutes,
            LocalDateTime applyDeadlineAt,
            boolean external,
            Set<PostingCategory> categories) {
        this.regionId = regionId;
        this.place = place;
        this.activityStartAt = activityStartAt;
        this.activityEndAt = activityEndAt;
        this.maxParticipants = maxParticipants;
        this.timeRecognized = timeRecognized;
        this.recognizedMinutes = recognizedMinutes;
        this.applyDeadlineAt = applyDeadlineAt;
        this.external = external;
        this.categories = new LinkedHashSet<>(categories);
    }
}
