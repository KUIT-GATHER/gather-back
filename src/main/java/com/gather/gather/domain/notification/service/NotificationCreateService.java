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
    private final NotificationSettingService notificationSettingService;

    /**
     * 현재 호출자의 트랜잭션에 참여해 알림을 생성한다. 핵심 비즈니스 처리와 알림 실패를 분리해야 하는 연동에서는 AFTER_COMMIT 이벤트 또는 별도 트랜잭션과 예외
     * 격리 정책을 사용해야 한다.
     */
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

    @Transactional
    public void createMeetingJoinResultNotification(
            Long recipientUserId, Long meetingId, String meetingName, boolean approved) {

        if (!notificationSettingService.isMeetingJoinResultEnabled(recipientUserId)) {
            return;
        }

        NotificationType type =
                approved
                        ? NotificationType.MEETING_JOIN_APPROVED
                        : NotificationType.MEETING_JOIN_REJECTED;

        String message =
                approved
                        ? "[%s] 가입이 승인되었어요. 지금부터 모임 활동에 참여할 수 있어요.".formatted(meetingName)
                        : "[%s] 가입이 거절되었어요. 다른 모임을 찾아보세요.".formatted(meetingName);

        create(recipientUserId, type, message, NotificationTargetType.MEETING, meetingId);
    }
}
