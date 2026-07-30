package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.entity.UserBadge;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import com.gather.gather.domain.notification.enums.NotificationTargetType;
import com.gather.gather.domain.notification.enums.NotificationType;
import com.gather.gather.domain.notification.service.NotificationCreateService;
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

    private final UserBadgeRepository userBadgeRepository;
    private final NotificationCreateService notificationCreateService;

    @Transactional
    public void award(Long userId, BadgeType badgeType) {
        if (userBadgeRepository.existsByUserIdAndBadgeType(userId, badgeType)) {
            return;
        }

        try {
            userBadgeRepository.saveAndFlush(UserBadge.create(userId, badgeType));
        } catch (DataIntegrityViolationException exception) {
            if (!isUniqueConstraintViolation(exception)) {
                throw exception;
            }
            log.warn("뱃지 지급 저장 중 유니크 제약 위반(중복 지급 방지됨). userId={}, badgeType={}", userId, badgeType);
            return;
        }

        notificationCreateService.create(
                userId,
                NotificationType.BADGE_EARNED,
                badgeType.getTitle() + " 뱃지를 획득했어요!",
                NotificationTargetType.MY_PAGE,
                null);
    }

    private boolean isUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
