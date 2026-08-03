package com.gather.gather.domain.notification.repository;

import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUser_IdAndCategoryAndDeletedAtIsNull(
            Long userId, NotificationCategory category, Pageable pageable);

    long countByUser_IdAndCategoryAndReadAtIsNullAndDeletedAtIsNull(
            Long userId, NotificationCategory category);

    Optional<Notification> findByIdAndUser_IdAndDeletedAtIsNull(Long notificationId, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            UPDATE Notification notification
            SET notification.readAt = :readAt
            WHERE notification.user.id = :userId
              AND notification.category = :category
              AND notification.readAt IS NULL
              AND notification.deletedAt IS NULL
            """)
    int markAllAsRead(
            @Param("userId") Long userId,
            @Param("category") NotificationCategory category,
            @Param("readAt") LocalDateTime readAt);
}
