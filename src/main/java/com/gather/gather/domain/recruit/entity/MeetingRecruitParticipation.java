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
 * <p>취소는 상태 전이({@code CANCELLED})로 처리하고 {@code (post_id, user_id)} UNIQUE로 중복 신청을 막는다 - 취소 후 재신청은 새
 * 행이 아니라 같은 행을 {@link #reapply(RecruitApplicantType)}로 되돌린다. 도메인 결합을 피해 post/user를 ID로만 보관한다({@code
 * PostingParticipation}과 동일 컨벤션).
 *
 * <p>이 파일은 팀장용 신청자 관리(반려/확정/출석) PR과 겹칠 수 있다 - 이 PR(모집공고 작성·조회·신청)에는 신청/취소/재신청만 필요해 그 범위만 구현했고,
 * 확정·반려·출석 관련 메서드는 후속 PR에서 추가한다.
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
    @Column(name = "applicant_type", nullable = false, length = 20)
    private RecruitApplicantType applicantType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetingRecruitParticipationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private MeetingRecruitParticipation(
            Long postId, Long userId, RecruitApplicantType applicantType) {
        this.postId = postId;
        this.userId = userId;
        this.applicantType = applicantType;
        this.status = MeetingRecruitParticipationStatus.APPLIED;
    }

    public static MeetingRecruitParticipation apply(
            Long postId, Long userId, RecruitApplicantType applicantType) {
        return new MeetingRecruitParticipation(postId, userId, applicantType);
    }

    /** 신청자 본인이 신청을 취소한다(물리 삭제 대신 상태 전이 - 마감 전 재신청을 허용하기 위함). */
    public void cancel() {
        this.status = MeetingRecruitParticipationStatus.CANCELLED;
    }

    /** 취소(CANCELLED) 상태에서 마감 전 다시 신청한다. 재신청 시점의 사용자 구분을 다시 기록한다. */
    public void reapply(RecruitApplicantType applicantType) {
        this.applicantType = applicantType;
        this.status = MeetingRecruitParticipationStatus.APPLIED;
    }

    /** 활동 후기를 작성해 완료(COMPLETED) 상태를 후기 작성됨(REVIEWED)으로 전환한다. */
    public void review() {
        this.status = MeetingRecruitParticipationStatus.REVIEWED;
    }

    /** 후기가 삭제되면 다시 작성할 수 있도록 완료(COMPLETED) 상태로 되돌린다. */
    public void unreview() {
        this.status = MeetingRecruitParticipationStatus.COMPLETED;
    }
}
