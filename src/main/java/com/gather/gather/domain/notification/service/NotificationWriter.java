package com.gather.gather.domain.notification.service;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.notification.entity.Notification;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAll(
            List<Long> recipientUserIds,
            NotificationType type,
            String message,
            NotificationTargetType targetType,
            Long targetId,
            Long targetMeetingId) {
        List<Long> distinctRecipientUserIds = recipientUserIds.stream().distinct().toList();
        if (distinctRecipientUserIds.isEmpty()) {
            return;
        }

        List<User> recipients = userRepository.findAllById(distinctRecipientUserIds);
        if (recipients.size() != distinctRecipientUserIds.size()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        List<Notification> notifications =
                recipients.stream()
                        .map(
                                recipient ->
                                        Notification.create(
                                                recipient,
                                                type,
                                                message,
                                                targetType,
                                                targetId,
                                                targetMeetingId))
                        .toList();

        notificationRepository.saveAll(notifications);
    }
}
