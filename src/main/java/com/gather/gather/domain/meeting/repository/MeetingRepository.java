package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.posting.entity.PostingCategory;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    Optional<Meeting> findByIdAndDeletedAtIsNull(Long id);

    // 가입 승인/거절 등 동시성 직렬화가 필요한 흐름에서 사용. 비관 락 필수(PR #95 보완분).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT m
            FROM Meeting m
            WHERE m.id = :meetingId
              AND m.deletedAt IS NULL
            """)
    Optional<Meeting> findByIdAndDeletedAtIsNullForUpdate(@Param("meetingId") Long meetingId);

    @Query(
            value =
                    """
                    SELECT m
                    FROM Meeting m
                    WHERE m.deletedAt IS NULL
                      AND (:keyword IS NULL
                           OR m.name LIKE CONCAT('%', :keyword, '%')
                           OR m.description LIKE CONCAT('%', :keyword, '%'))
                      AND (:hasRegionFilter = false OR m.regionId IN :regionIds)
                      AND (:category IS NULL OR m.category = :category)
                      AND (:status IS NULL OR m.status = :status)
                      AND (:recruitingOnly = false
                           OR (m.deadline >= :now
                               AND (m.activityEndAt IS NULL OR m.activityEndAt >= :now)
                               AND m.currentMemberCount < m.maxMember))
                      AND (:activityStartAt IS NULL OR m.activityEndAt >= :activityStartAt)
                      AND (:activityEndAt IS NULL OR m.activityStartAt <= :activityEndAt)
                    ORDER BY CASE
                        WHEN :postingBasedFirst = true AND m.volunteerPostingId IS NULL THEN 1
                        ELSE 0
                    END
                    """,
            countQuery =
                    """
                    SELECT COUNT(m)
                    FROM Meeting m
                    WHERE m.deletedAt IS NULL
                      AND (:keyword IS NULL
                           OR m.name LIKE CONCAT('%', :keyword, '%')
                           OR m.description LIKE CONCAT('%', :keyword, '%'))
                      AND (:hasRegionFilter = false OR m.regionId IN :regionIds)
                      AND (:category IS NULL OR m.category = :category)
                      AND (:status IS NULL OR m.status = :status)
                      AND (:recruitingOnly = false
                           OR (m.deadline >= :now
                               AND (m.activityEndAt IS NULL OR m.activityEndAt >= :now)
                               AND m.currentMemberCount < m.maxMember))
                      AND (:activityStartAt IS NULL OR m.activityEndAt >= :activityStartAt)
                      AND (:activityEndAt IS NULL OR m.activityStartAt <= :activityEndAt)
                    """)
    Page<Meeting> searchMeetings(
            @Param("keyword") String keyword,
            @Param("hasRegionFilter") boolean hasRegionFilter,
            @Param("regionIds") List<Long> regionIds,
            @Param("category") PostingCategory category,
            @Param("status") MeetingStatus status,
            @Param("recruitingOnly") boolean recruitingOnly,
            @Param("now") LocalDateTime now,
            @Param("activityStartAt") LocalDateTime activityStartAt,
            @Param("activityEndAt") LocalDateTime activityEndAt,
            @Param("postingBasedFirst") Boolean postingBasedFirst,
            Pageable pageable);

    Page<Meeting> findAllByVolunteerPostingIdAndDeletedAtIsNull(
            Long volunteerPostingId, Pageable pageable);
}
