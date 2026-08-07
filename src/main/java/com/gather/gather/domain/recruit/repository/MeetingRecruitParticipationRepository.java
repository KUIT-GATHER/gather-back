package com.gather.gather.domain.recruit.repository;

import com.gather.gather.domain.post.enums.PostType;
import com.gather.gather.domain.recruit.dto.MyAppliedRecruitResponse;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipation;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRecruitParticipationRepository
        extends JpaRepository<MeetingRecruitParticipation, Long> {

    Optional<MeetingRecruitParticipation> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    long countByPostId(Long postId);

    /** 나의 활동 - 내가 이 모임에서 신청한 모집공고 목록(활동일 최신순). Post·MeetingRecruit를 postId로 조인한다. */
    @Query(
            value =
                    """
                    SELECT new com.gather.gather.domain.recruit.dto.MyAppliedRecruitResponse(
                        p.id, p.meeting.id, p.title, r.place, r.actDate, r.actStartTime,
                        r.actEndTime, prt.status)
                    FROM MeetingRecruitParticipation prt
                    JOIN Post p ON p.id = prt.postId
                    JOIN MeetingRecruit r ON r.postId = prt.postId
                    WHERE prt.userId = :userId
                      AND p.meeting.id = :meetingId
                      AND p.deletedAt IS NULL
                    ORDER BY r.actDate DESC, p.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(prt)
                    FROM MeetingRecruitParticipation prt
                    JOIN Post p ON p.id = prt.postId
                    WHERE prt.userId = :userId
                      AND p.meeting.id = :meetingId
                      AND p.deletedAt IS NULL
                    """)
    Page<MyAppliedRecruitResponse> findMyAppliedRecruits(
            @Param("userId") Long userId, @Param("meetingId") Long meetingId, Pageable pageable);

    /** 나의 활동 요약 - 내가 이 모임에서 신청한 모집공고 수. */
    @Query(
            """
            SELECT COUNT(prt)
            FROM MeetingRecruitParticipation prt
            JOIN Post p ON p.id = prt.postId
            WHERE prt.userId = :userId
              AND p.meeting.id = :meetingId
              AND p.deletedAt IS NULL
            """)
    long countMyAppliedRecruits(@Param("userId") Long userId, @Param("meetingId") Long meetingId);

    /** 모임 완료 시, 해당 모임 모집공고 참여의 상태를 일괄 전환한다(신청 → 봉사완료). 벌크 업데이트. */
    @Modifying(clearAutomatically = true)
    @Query(
            """
            UPDATE MeetingRecruitParticipation prt
            SET prt.status = :toStatus
            WHERE prt.status = :fromStatus
              AND prt.postId IN (
                  SELECT p.id
                  FROM Post p
                  WHERE p.meeting.id = :meetingId
                    AND p.type = :recruitType
                    AND p.deletedAt IS NULL
              )
            """)
    int updateStatusByMeeting(
            @Param("meetingId") Long meetingId,
            @Param("fromStatus") MeetingRecruitParticipationStatus fromStatus,
            @Param("toStatus") MeetingRecruitParticipationStatus toStatus,
            @Param("recruitType") PostType recruitType);
}
