package com.gather.gather.domain.notification.repository;

import com.gather.gather.domain.notification.entity.NotificationSetting;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByUser_Id(Long userId);

    @Query(
            """
            SELECT setting.user.id
            FROM NotificationSetting setting
            WHERE setting.user.id IN :userIds
              AND setting.volunteerScheduleEnabled = false
            """)
    List<Long> findVolunteerScheduleDisabledUserIds(@Param("userIds") Collection<Long> userIds);

    @Query(
            """
            SELECT setting.user.id
            FROM NotificationSetting setting
            WHERE setting.user.id IN :userIds
              AND setting.bookmarkedMeetingDeadlineEnabled = true
            """)
    List<Long> findBookmarkedMeetingDeadlineEnabledUserIds(
            @Param("userIds") Collection<Long> userIds);

    @Query(
            """
            SELECT setting.user.id
            FROM NotificationSetting setting
            WHERE setting.user.id IN :userIds
              AND setting.bookmarkedPostingDeadlineEnabled = true
            """)
    List<Long> findBookmarkedPostingDeadlineEnabledUserIds(
            @Param("userIds") Collection<Long> userIds);
}
