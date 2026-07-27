package com.gather.gather.domain.notification.repository;

import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUser_IdAndCategoryAndDeletedAtIsNull(
            Long userId, NotificationCategory category, Pageable pageable);
}
