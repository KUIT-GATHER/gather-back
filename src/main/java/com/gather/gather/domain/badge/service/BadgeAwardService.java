package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.entity.UserBadge;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.repository.NotificationSettingRepository;
import com.gather.gather.domain.notification.service.NotificationCreateService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 뱃지 지급은 1회만 이뤄진다(idempotent) — 이미 획득한 뱃지는 조용히 무시한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeAwardService {

    /** V37 마이그레이션에서 정의한 (user_id, badge_type) 복합 유니크 제약. */
    private static final String USER_BADGE_UNIQUE_CONSTRAINT = "uq_user_badge_user_type";

    private final UserBadgeRepository userBadgeRepository;
    private final NotificationCreateService notificationCreateService;
    private final NotificationSettingRepository notificationSettingRepository;

    @Transactional
    public void award(Long userId, BadgeType badgeType) {
        if (userBadgeRepository.existsByUserIdAndBadgeType(userId, badgeType)) {
            return;
        }

        try {
            userBadgeRepository.saveAndFlush(UserBadge.create(userId, badgeType));
        } catch (DataIntegrityViolationException exception) {
            if (!isUserBadgeUniqueConstraintViolation(exception)) {
                throw exception;
            }
            log.warn("뱃지 지급 저장 중 유니크 제약 위반(중복 지급 방지됨). userId={}, badgeType={}", userId, badgeType);
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

    /** 알림 설정이 없는 사용자는 NotificationSetting의 기본값(badgeEnabled=false)과 동일하게 취급해 발송하지 않는다. */
    private boolean isBadgeNotificationEnabled(Long userId) {
        return notificationSettingRepository
                .findByUser_Id(userId)
                .map(setting -> setting.isBadgeEnabled())
                .orElse(false);
    }

    // 테이블에는 (user_id, badge_type) 유니크 제약 외에도 user FK가 걸려 있어, 그 위반까지 전부 "중복 지급"으로
    // 오응답하지 않도록 실제 위반된 제약 이름을 확인한다.
    private boolean isUserBadgeUniqueConstraintViolation(
            DataIntegrityViolationException exception) {
        String constraintName = findConstraintName(exception);
        if (constraintName != null) {
            return constraintName
                    .replace("`", "")
                    .replace("\"", "")
                    .toLowerCase(Locale.ROOT)
                    .contains(USER_BADGE_UNIQUE_CONSTRAINT);
        }
        return hasConstraintNameInMessage(exception);
    }

    private String findConstraintName(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                    && constraintViolationException.getConstraintName() != null) {
                return constraintViolationException.getConstraintName();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private boolean hasConstraintNameInMessage(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT).contains(USER_BADGE_UNIQUE_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
