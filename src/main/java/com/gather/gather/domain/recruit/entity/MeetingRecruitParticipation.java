package com.gather.gather.domain.recruit.entity;

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

/**
 * 모집공고(RECRUIT post) 참여신청.
 *
 * <p>취소는 물리 삭제로 처리하고 {@code (post_id, user_id)} UNIQUE로 중복 신청을 막는다. 도메인 결합을 피해 post/user를 ID로만
 * 보관한다({@code PostingParticipation}과 동일 컨벤션).
 */
@Entity
@Getter
@Table(
        name = "meeting_recruit_participation",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_meeting_recruit_participation_post_user",
                    columnNames = {"post_id", "user_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingRecruitParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingRecruitParticipationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private MeetingRecruitParticipation(Long postId, Long userId) {
        this.postId = postId;
        this.userId = userId;
        this.status = MeetingRecruitParticipationStatus.APPLIED;
    }

    public static MeetingRecruitParticipation apply(Long postId, Long userId) {
        return new MeetingRecruitParticipation(postId, userId);
    }

    /** 팀장이 신청(APPLIED)을 확정한다. */
    public void confirm() {
        this.status = MeetingRecruitParticipationStatus.CONFIRMED;
    }

    /** 활동 종료 후 팀장이 확정(CONFIRMED) 참가자의 참석을 처리해 봉사완료로 전환한다. */
    public void complete() {
        this.status = MeetingRecruitParticipationStatus.COMPLETED;
    }
}
