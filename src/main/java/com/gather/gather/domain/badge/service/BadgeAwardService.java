package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.domain.notification.service.NotificationCreateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 뱃지 지급은 1회만 이뤄진다(idempotent) — 이미 획득한 뱃지는 조용히 무시한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeAwardService {

    private final UserBadgeRepository userBadgeRepository;
    private final UserBadgeWriter userBadgeWriter;
    private final NotificationCreateService notificationCreateService;
    private final NotificationSettingRepository notificationSettingRepository;

    @Transactional
    public void award(Long userId, BadgeType badgeType) {
        if (userBadgeRepository.existsByUserIdAndBadgeType(userId, badgeType)) {
            return;
        }

        boolean newlyAwarded = userBadgeWriter.tryInsert(userId, badgeType);
        if (!newlyAwarded) {
            return;
        }

        if (!isBadgeNotificationEnabled(userId)) {
            return;
        }

        try {
            notificationCreateService.create(
                    userId,
                    NotificationType.BADGE_EARNED,
                    badgeType.getTitle() + " 뱃지를 획득했어요!",
                    NotificationTargetType.MY_PAGE,
                    null);
        } catch (RuntimeException exception) {
            log.warn(
                    "뱃지 획득 알림 발송 실패(뱃지 지급 자체는 유지됨). userId={}, badgeType={}",
                    userId,
                    badgeType,
                    exception);
        }
    }

    /** 알림 설정이 없는 사용자는 NotificationSetting의 기본값(badgeEnabled=true)을 적용한다. */
    private boolean isBadgeNotificationEnabled(Long userId) {
        return notificationSettingRepository
                .findByUser_Id(userId)
                .map(setting -> setting.isBadgeEnabled())
                .orElse(true);
    }
}
