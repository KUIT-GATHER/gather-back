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
 * <p>이 파일은 모집공고 작성·조회·신청(다른 PR)과 겹칠 수 있다 - 이 PR(팀장용 신청자 관리)에서는 반려·일괄확정·출석 처리가 추가로 필요해
 * applicantType·attendanceStatus·recognizedMinutesApplied 필드와
 * reject()/confirm()/markPresent()/markAbsent()를 포함한 상위 집합으로 작성했다. 두 PR을 모두 반영할 때는 필드·메서드 합집합을 유지하면
 * 된다.
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

    /** 출석 처리로 실제 반영된 인정 시간(분). ABSENT로 되돌릴 때 정확히 차감하기 위해 적용된 값을 그대로 보관한다. */
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

    /** 취소(CANCELLED) 상태에서 마감 전 다시 신청한다. */
    public void reapply(RecruitApplicantType applicantType) {
        this.applicantType = applicantType;
        this.status = MeetingRecruitParticipationStatus.APPLIED;
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

    /** 활동 종료 후 출석 처리 - 결석. 확정(CONFIRMED) 상태로 유지하고 이미 반영된 인정 시간이 있으면 차감(0으로)한다. 이미 ABSENT면 멱등. */
    public void markAbsent() {
        if (attendanceStatus == RecruitAttendanceStatus.ABSENT) {
            return;
        }
        this.attendanceStatus = RecruitAttendanceStatus.ABSENT;
        this.status = MeetingRecruitParticipationStatus.CONFIRMED;
        this.recognizedMinutesApplied = 0;
    }
}
