package com.gather.gather.domain.meeting.repository;

import com.gather.gather.domain.meeting.entity.MeetingImageUpload;
import com.gather.gather.domain.meeting.entity.MeetingImageUploadStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MeetingImageUploadRepository extends JpaRepository<MeetingImageUpload, Long> {

    long countByMeetingIdAndStatusAndExpiresAtAfter(
            Long meetingId, MeetingImageUploadStatus status, LocalDateTime now);

    Optional<MeetingImageUpload> findByMeetingIdAndObjectKey(Long meetingId, String objectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select u from MeetingImageUpload u
            where u.meetingId = :meetingId
              and u.objectKey = :objectKey
            """)
    Optional<MeetingImageUpload> findByMeetingIdAndObjectKeyForUpdate(
            Long meetingId, String objectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select u from MeetingImageUpload u
            where u.status = :status
              and u.expiresAt <= :now
            order by u.id
            """)
    List<MeetingImageUpload> findExpiredForUpdate(
            MeetingImageUploadStatus status, LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select u from MeetingImageUpload u
            where u.status = :status
              and u.objectDeleted = false
            order by u.id
            """)
    List<MeetingImageUpload> findDeletionPendingForUpdate(
            MeetingImageUploadStatus status, Pageable pageable);
}