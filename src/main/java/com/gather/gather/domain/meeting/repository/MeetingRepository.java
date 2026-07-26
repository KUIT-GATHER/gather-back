package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.posting.entity.PostingCategory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    Optional<Meeting> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :id and m.deletedAt is null")
    Optional<Meeting> findByIdForUpdate(Long id);

    @Query(
            """
            SELECT m
            FROM Meeting m
            WHERE m.id = :meetingId
              AND m.deletedAt IS NULL
            """)
    Optional<Meeting> findByIdAndDeletedAtIsNullForUpdate(@Param("meetingId") Long meetingId);

    @Query(
            """
            SELECT m
            FROM Meeting m
            WHERE m.deletedAt IS NULL
              AND (:keyword IS NULL
                   OR m.name LIKE CONCAT('%', :keyword, '%')
                   OR m.description LIKE CONCAT('%', :keyword, '%'))
              AND (:regionId IS NULL OR m.regionId = :regionId)
              AND (:category IS NULL OR m.category = :category)
              AND (:status IS NULL OR m.status = :status)
            """)
    Page<Meeting> searchMeetings(
            @Param("keyword") String keyword,
            @Param("regionId") Long regionId,
            @Param("category") PostingCategory category,
            @Param("status") MeetingStatus status,
            Pageable pageable);

    Page<Meeting> findAllByVolunteerPostingIdAndDeletedAtIsNull(
            Long volunteerPostingId, Pageable pageable);
}
