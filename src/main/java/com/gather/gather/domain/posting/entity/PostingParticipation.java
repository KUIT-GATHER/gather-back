package com.gather.gather.domain.posting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Table(
        name = "posting_participation",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_posting_participation_user_posting",
                    columnNames = {"user_id", "posting_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostingParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "posting_id", nullable = false)
    private Long postingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostingParticipationStatus status;

    /** 완료 처리 이후 사용자가 직접 입력하는 봉사 인정시간(분 단위, 10분 단위 입력). */
    @Column(name = "recognized_minutes")
    private Integer recognizedMinutes;

    /**
     * 완료 처리 시점(complete() 호출 시각). 뱃지 판정(연속 참여 월 계산)에 이 값을 사용한다 — updatedAt은
     * submitRecognizedMinutes() 등 완료 이후의 다른 변경으로도 갱신되므로 완료 시점 대용으로 쓸 수 없다.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PostingParticipation(Long userId, Long postingId, PostingParticipationStatus status) {
        this.userId = userId;
        this.postingId = postingId;
        this.status = status;
    }

    public static PostingParticipation create(Long userId, Long postingId) {
        return new PostingParticipation(userId, postingId, PostingParticipationStatus.APPLIED);
    }

    public void complete() {
        this.status = PostingParticipationStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /** 활동 후기를 작성해 완료(COMPLETED) 상태를 후기 작성됨(REVIEWED)으로 전환한다. */
    public void review() {
        this.status = PostingParticipationStatus.REVIEWED;
    }

    /** 후기가 삭제되면 다시 작성할 수 있도록 완료(COMPLETED) 상태로 되돌린다. */
    public void unreview() {
        this.status = PostingParticipationStatus.COMPLETED;
    }

    public void submitRecognizedMinutes(Integer recognizedMinutes) {
        this.recognizedMinutes = recognizedMinutes;
    }
}
