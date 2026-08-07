package com.gather.gather.domain.recruit.repository;

import com.gather.gather.domain.recruit.dto.MyAppliedRecruitResponse;
import com.gather.gather.domain.recruit.dto.ReviewableRecruitActivity;
import com.gather.gather.domain.recruit.entity.MeetingRecruitParticipation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /**
     * 모임 해산 가능 여부 판단용: 아직 활동일이 지나지 않은 모집공고에 CONFIRMED 참가자가 있는지 확인한다. actDate 기준 날짜 단위로만 비교한다(당일이면
     * 아직 "종료되지 않음"으로 간주해 보수적으로 막는다).
     */
    @Query(
            """
            SELECT COUNT(prt) > 0
            FROM MeetingRecruitParticipation prt
            JOIN Post p ON p.id = prt.postId
            JOIN MeetingRecruit r ON r.postId = prt.postId
            WHERE p.meeting.id = :meetingId
              AND p.deletedAt IS NULL
              AND prt.status = com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus.CONFIRMED
              AND r.actDate >= :today
            """)
    boolean existsConfirmedParticipantWithUpcomingActivity(
            @Param("meetingId") Long meetingId, @Param("today") LocalDate today);

    /**
     * 활동 후기 작성 가능 목록(MEETING_RECRUIT 출처) - 이 모임에서 내가 COMPLETED(참석 처리되어 완료)된 모집공고 참여를 조회한다. 이미 후기를
     * 작성한(REVIEWED) 참여는 제외한다.
     */
    @Query(
            """
            SELECT new com.gather.gather.domain.recruit.dto.ReviewableRecruitActivity(
                p.id, p.title, r.actDate, r.actStartTime, r.actEndTime)
            FROM MeetingRecruitParticipation prt
            JOIN Post p ON p.id = prt.postId
            JOIN MeetingRecruit r ON r.postId = prt.postId
            WHERE prt.userId = :userId
              AND p.meeting.id = :meetingId
              AND p.deletedAt IS NULL
              AND prt.status = com.gather.gather.domain.recruit.entity.MeetingRecruitParticipationStatus.COMPLETED
            ORDER BY r.actDate DESC, p.id DESC
            """)
    List<ReviewableRecruitActivity> findReviewableActivities(
            @Param("userId") Long userId, @Param("meetingId") Long meetingId);
}
