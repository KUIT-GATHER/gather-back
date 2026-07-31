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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCreateService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * 항상 별도의 새 트랜잭션에서 알림을 생성한다(REQUIRES_NEW). 호출자 트랜잭션에 참여(REQUIRED)하면 알림 생성 실패가 호출자 트랜잭션 전체를
     * rollback-only로 만들어, catch로 격리한 것처럼 보여도 실제로는 핵심 비즈니스 처리까지 롤백시킨다.
     */
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
}
