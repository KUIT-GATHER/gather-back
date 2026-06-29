package com.gather.gather.domain.meeting.repository;
import com.gather.gather.domain.meeting.entity.Meeting;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    Optional<Meeting> findByIdAndDeletedAtIsNull(Long id);
    @Query(
            """
            SELECT m
            FROM Meeting m
            WHERE m.deletedAt IS NULL
              AND (:keyword IS NULL
                   OR m.name LIKE CONCAT('%', :keyword, '%')
                   OR m.description LIKE CONCAT('%', :keyword, '%'))
              AND (:regionId IS NULL OR m.regionId = :regionId)
              AND (:categoryId IS NULL OR m.categoryId = :categoryId)
              AND (:status IS NULL OR m.status = :status)
            ORDER BY m.createdAt DESC
            """)
    List<Meeting> searchMeetings(
            @Param("keyword") String keyword,
            @Param("regionId") Long regionId,
            @Param("categoryId") Long categoryId,
            @Param("status") MeetingStatus status);
}