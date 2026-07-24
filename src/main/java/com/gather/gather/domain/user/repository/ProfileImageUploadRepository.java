package com.gather.gather.domain.user.repository;

import com.gather.gather.domain.user.entity.ProfileImageUpload;
import com.gather.gather.domain.user.entity.ProfileImageUploadStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProfileImageUploadRepository extends JpaRepository<ProfileImageUpload, Long> {

    long countByUserIdAndStatusAndExpiresAtAfter(
            Long userId, ProfileImageUploadStatus status, LocalDateTime now);

    Optional<ProfileImageUpload> findByUserIdAndObjectKey(Long userId, String objectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select upload from ProfileImageUpload upload
            where upload.userId = :userId
              and upload.objectKey = :objectKey
            """)
    Optional<ProfileImageUpload> findByUserIdAndObjectKeyForUpdate(Long userId, String objectKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select upload from ProfileImageUpload upload where upload.id = :id")
    Optional<ProfileImageUpload> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select upload from ProfileImageUpload upload
            where upload.status = :status
              and upload.expiresAt <= :now
            order by upload.id
            """)
    List<ProfileImageUpload> findExpiredForUpdate(
            ProfileImageUploadStatus status, LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select upload from ProfileImageUpload upload
            where upload.status = :status
              and upload.previousObjectKey is not null
              and (upload.previousObjectDeleted = false
                   or upload.appliedAt <= :safeBefore)
            order by upload.id
            """)
    List<ProfileImageUpload> findPreviousDeletionPendingForUpdate(
            ProfileImageUploadStatus status, LocalDateTime safeBefore, Pageable pageable);
}
