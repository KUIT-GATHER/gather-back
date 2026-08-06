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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 모임 내부 모집공고(RECRUIT 게시글)의 확장 정보. {@code post_id}로 RECRUIT 유형 {@code Post}와 1:1 대응한다. 도메인 결합을 피해
 * post를 연관관계 대신 ID로 보관한다.
 *
 * <p>봉사시간 인정({@code timeRecognized}/{@code recognizedMinutes})과 외부 공개({@code isExternal})는 현재 값만
 * 저장하고 실제 반영/공개 로직은 후속으로 둔다.
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

    @Column(nullable = false, length = 255)
    private String place;

    @Column(name = "act_date", nullable = false)
    private LocalDate actDate;

    @Column(name = "act_start_time")
    private LocalTime actStartTime;

    @Column(name = "act_end_time")
    private LocalTime actEndTime;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "time_recognized", nullable = false)
    private boolean timeRecognized;

    @Column(name = "recognized_minutes")
    private Integer recognizedMinutes;

    @Column(name = "apply_deadline", nullable = false)
    private LocalDate applyDeadline;

    @Column(name = "is_external", nullable = false)
    private boolean isExternal;

    @Column(name = "participation_condition", columnDefinition = "TEXT")
    private String participationCondition;

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
            String place,
            LocalDate actDate,
            LocalTime actStartTime,
            LocalTime actEndTime,
            int maxParticipants,
            boolean timeRecognized,
            Integer recognizedMinutes,
            LocalDate applyDeadline,
            boolean isExternal,
            Set<PostingCategory> categories,
            String participationCondition) {
        this.postId = postId;
        this.place = place;
        this.actDate = actDate;
        this.actStartTime = actStartTime;
        this.actEndTime = actEndTime;
        this.maxParticipants = maxParticipants;
        this.timeRecognized = timeRecognized;
        this.recognizedMinutes = recognizedMinutes;
        this.applyDeadline = applyDeadline;
        this.isExternal = isExternal;
        this.categories = new LinkedHashSet<>(categories);
        this.participationCondition = participationCondition;
    }

    public static MeetingRecruit create(
            Long postId,
            String place,
            LocalDate actDate,
            LocalTime actStartTime,
            LocalTime actEndTime,
            int maxParticipants,
            boolean timeRecognized,
            Integer recognizedMinutes,
            LocalDate applyDeadline,
            boolean isExternal,
            Set<PostingCategory> categories,
            String participationCondition) {
        return new MeetingRecruit(
                postId,
                place,
                actDate,
                actStartTime,
                actEndTime,
                maxParticipants,
                timeRecognized,
                recognizedMinutes,
                applyDeadline,
                isExternal,
                categories,
                participationCondition);
    }

    /** 신청 가능 기간인지: 오늘이 신청 마감일 이하이면 열려 있다(마감일 당일까지 신청 가능). */
    public boolean isApplicationOpen(LocalDate today) {
        return !today.isAfter(applyDeadline);
    }

    /** 모집공고 확장 필드 수정. 제목·내용은 연결된 Post에서 별도로 갱신한다. */
    public void update(
            String place,
            LocalDate actDate,
            LocalTime actStartTime,
            LocalTime actEndTime,
            int maxParticipants,
            boolean timeRecognized,
            Integer recognizedMinutes,
            LocalDate applyDeadline,
            boolean isExternal,
            Set<PostingCategory> categories,
            String participationCondition) {
        this.place = place;
        this.actDate = actDate;
        this.actStartTime = actStartTime;
        this.actEndTime = actEndTime;
        this.maxParticipants = maxParticipants;
        this.timeRecognized = timeRecognized;
        this.recognizedMinutes = recognizedMinutes;
        this.applyDeadline = applyDeadline;
        this.isExternal = isExternal;
        this.categories = new LinkedHashSet<>(categories);
        this.participationCondition = participationCondition;
    }
}
