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
import java.time.LocalDate;
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

    /**
     * 사용자가 외부 1365/VMS 신청 완료 후 Gather에서 직접 등록한 개인 참여 일정 시작일. 단일 날짜 신청도 종료일과 동일한 값으로 저장한다.
     *
     * <p>2026-08 정책 변경 이전에 생성된 기존 데이터는 이 값이 null일 수 있다(QA 환경은 초기화 예정). null인 동안에는 완료 가능 시점,
     * 마이페이지 일정, 활동기록, 알림에서 {@link com.gather.gather.domain.posting.entity.Posting}의 전체 활동기간으로
     * fallback한다.
     */
    @Column(name = "participation_start_date")
    private LocalDate participationStartDate;

    /** 개인 참여 일정 종료일. {@link #participationStartDate}와 함께 저장되며 단일 날짜 신청 시 시작일과 동일하다. */
    @Column(name = "participation_end_date")
    private LocalDate participationEndDate;

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

    private PostingParticipation(
            Long userId,
            Long postingId,
            PostingParticipationStatus status,
            LocalDate participationStartDate,
            LocalDate participationEndDate) {
        this.userId = userId;
        this.postingId = postingId;
        this.status = status;
        this.participationStartDate = participationStartDate;
        this.participationEndDate = participationEndDate;
    }

    /**
     * 외부 1365/VMS 신청 완료 후 사용자가 Gather에 등록하는 개인 참여 일정으로 참여를 생성한다. participationStartDate/EndDate는
     * 호출부(Service)에서 공고 활동기간·과거일자 검증을 마친 값이어야 한다.
     */
    public static PostingParticipation create(
            Long userId,
            Long postingId,
            LocalDate participationStartDate,
            LocalDate participationEndDate) {
        return new PostingParticipation(
                userId,
                postingId,
                PostingParticipationStatus.APPLIED,
                participationStartDate,
                participationEndDate);
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

    /** 완료 가능 시점 판정에 쓰는 실질 종료일. 개인 일정이 없는 기존 데이터는 공고 전체 종료일로 fallback한다. */
    public LocalDate resolveEffectiveEndDate(LocalDate postingFallbackEndDate) {
        return participationEndDate != null ? participationEndDate : postingFallbackEndDate;
    }

    /** 알림 스케줄러 기준일 판정에 쓰는 실질 시작일. 개인 일정이 없는 기존 데이터는 공고 전체 시작일로 fallback한다. */
    public LocalDate resolveEffectiveStartDate(LocalDate postingFallbackStartDate) {
        return participationStartDate != null ? participationStartDate : postingFallbackStartDate;
    }
}
