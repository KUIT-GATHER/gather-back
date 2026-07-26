package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCreateService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public Notification create(
            Long recipientUserId,
            NotificationType type,
            String message,
            NotificationTargetType targetType,
            Long targetId) {
        User recipient =
                userRepository
                        .findById(recipientUserId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Notification notification =
                Notification.create(recipient, type, message, targetType, targetId);
        return notificationRepository.save(notification);
    }
}
