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
 * <p>이 파일은 여러 PR이 각자의 범위만큼 확장한다: 활동 후기 PR은 {@link #review()}/{@link #unreview()}를, 팀장용 신청자 관리 PR은
 * {@code attendanceStatus}/{@code recognizedMinutesApplied} 필드와 {@link #reject()}/{@link
 * #confirm()}/ {@link #markPresent(int)}/{@link #markAbsent()}를 추가한다. 병합 시 각 PR이 추가한 필드·메서드를 모두
 * 유지한다.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 20)
    private RecruitAttendanceStatus attendanceStatus;

    /** 출석 처리로 실제 반영된 인정 시간(분). ABSENT로 되돌릴 때 정확히 차감하기 위해 적용한 값을 그대로 보관한다. */
    @Column(name = "recognized_minutes_applied", nullable = false)
    private int recognizedMinutesApplied;

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
        this.attendanceStatus = RecruitAttendanceStatus.UNSET;
        this.recognizedMinutesApplied = 0;
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

    /** 활동 후기를 작성하면 완료(COMPLETED) 상태를 후기 작성됨(REVIEWED)으로 전환한다. */
    public void review() {
        this.status = MeetingRecruitParticipationStatus.REVIEWED;
    }

    /** 후기가 삭제되면 다시 작성할 수 있도록 완료(COMPLETED) 상태로 되돌린다. */
    public void unreview() {
        this.status = MeetingRecruitParticipationStatus.COMPLETED;
    }

    /** 팀장이 신청(APPLIED)을 반려한다. */
    public void reject() {
        this.status = MeetingRecruitParticipationStatus.REJECTED;
    }

    /** 팀장이 신청(APPLIED)을 확정한다(일괄 확정에서 신청자별로 호출). */
    public void confirm() {
        this.status = MeetingRecruitParticipationStatus.CONFIRMED;
    }

    /** 활동 종료 후 출석 처리 - 참석. 완료(COMPLETED)로 전환하고 인정 시간을 반영한다. 이미 PRESENT면 아무 것도 하지 않는다(멱등). */
    public void markPresent(int recognizedMinutesToApply) {
        if (attendanceStatus == RecruitAttendanceStatus.PRESENT) {
            return;
        }
        this.attendanceStatus = RecruitAttendanceStatus.PRESENT;
        this.status = MeetingRecruitParticipationStatus.COMPLETED;
        this.recognizedMinutesApplied = recognizedMinutesToApply;
    }

    /** 활동 종료 후 출석 처리 - 불참. 확정(CONFIRMED) 상태로 유지하고, 이미 반영된 인정 시간이 있으면 차감(0으로)한다. 이미 ABSENT면 멱등. */
    public void markAbsent() {
        if (attendanceStatus == RecruitAttendanceStatus.ABSENT) {
            return;
        }
        this.attendanceStatus = RecruitAttendanceStatus.ABSENT;
        this.status = MeetingRecruitParticipationStatus.CONFIRMED;
        this.recognizedMinutesApplied = 0;
    }
}
