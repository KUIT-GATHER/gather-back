package com.gather.gather.domain.notification.repository;

import com.gather.gather.domain.notification.entity.NotificationSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByUser_Id(Long userId);
}
