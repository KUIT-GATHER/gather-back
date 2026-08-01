package com.gather.gather.domain.badge.service;

import com.gather.gather.domain.badge.entity.BadgeType;
import com.gather.gather.domain.badge.entity.UserBadge;
import com.gather.gather.domain.badge.repository.UserBadgeRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 뱃지 INSERT를 별도의 새 트랜잭션(REQUIRES_NEW)에서 수행한다.
 *
 * <p>이 메서드가 호출자의 트랜잭션에 REQUIRED로 참여했다면, 유니크 제약 위반(중복 지급 경합)을 여기서 catch하더라도 호출자의 트랜잭션은 이미
 * rollback-only로 마킹된 뒤라 커밋 시점에 {@code UnexpectedRollbackException}이 발생한다. {@code
 * BadgeEvaluationService}처럼 한 트랜잭션에서 여러 뱃지를 순차 판정하는 경로에서는 이 때문에 하나의 중복 지급 경합이 나머지 뱃지 지급까지 함께
 * 무효화시킨다. REQUIRES_NEW로 분리하면 이 메서드의 실패는 자신의 트랜잭션에만 국한된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBadgeWriter {

    /** V37 마이그레이션에서 정의한 (user_id, badge_type) 복합 유니크 제약. */
    private static final String USER_BADGE_UNIQUE_CONSTRAINT = "uq_user_badge_user_type";

    private final UserBadgeRepository userBadgeRepository;

    /** 새로 지급되었으면 true, 이미 지급된 뱃지와 경합해 유니크 제약에 걸렸으면 false를 반환한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryInsert(Long userId, BadgeType badgeType) {
        try {
            userBadgeRepository.saveAndFlush(UserBadge.create(userId, badgeType));
            return true;
        } catch (DataIntegrityViolationException exception) {
            if (!isUserBadgeUniqueConstraintViolation(exception)) {
                throw exception;
            }
            log.warn("뱃지 지급 저장 중 유니크 제약 위반(중복 지급 방지됨). userId={}, badgeType={}", userId, badgeType);
            return false;
        }
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
