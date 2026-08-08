package com.gather.gather.domain.recruit.repository;

import com.gather.gather.domain.recruit.dto.RecruitManageItem;
import com.gather.gather.domain.recruit.entity.MeetingRecruit;
import com.gather.gather.domain.recruit.entity.RecruitConfirmationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRecruitRepository extends JpaRepository<MeetingRecruit, Long> {

    Optional<MeetingRecruit> findByPostId(Long postId);

    boolean existsByPostId(Long postId);

    /** 마감 자동 확정 배치용 - 신청 마감 시각이 지났는데 아직 확정되지 않은 모집공고. */
    List<MeetingRecruit> findAllByConfirmationStatusAndApplyDeadlineAtBefore(
            RecruitConfirmationStatus confirmationStatus, LocalDateTime applyDeadlineAt);

    /** 팀장용 활동 관리(#12) - 모임에서 작성한 모집공고 전체(페이지네이션 없음). */
    @Query(
            """
            SELECT new com.gather.gather.domain.recruit.dto.RecruitManageItem(
                p.id, p.title, r.place, r.activityStartAt, r.activityEndAt, r.applyDeadlineAt,
                (SELECT COUNT(prt) FROM MeetingRecruitParticipation prt
                 WHERE prt.postId = p.id AND prt.status IN (
                     com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus.APPLIED,
                     com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus.CONFIRMED,
                     com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus.COMPLETED,
                     com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus.REVIEWED)),
                r.maxParticipants, r.external, r.confirmationStatus, r.confirmedAt)
            FROM MeetingRecruit r
            JOIN Post p ON p.id = r.postId
            WHERE p.meeting.id = :meetingId
              AND p.deletedAt IS NULL
            ORDER BY r.activityStartAt DESC
            """)
    List<RecruitManageItem> findManageItemsByMeetingId(@Param("meetingId") Long meetingId);
}
