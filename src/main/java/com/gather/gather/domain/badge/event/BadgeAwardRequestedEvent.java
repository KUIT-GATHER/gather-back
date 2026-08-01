package com.gather.gather.domain.badge.event;

import com.gather.gather.domain.badge.entity.BadgeType;

/** 단일 뱃지 즉시 지급 요청. 커밋 이후(AFTER_COMMIT)에 처리되어 본 트랜잭션에 영향을 주지 않는다. */
public record BadgeAwardRequestedEvent(Long userId, BadgeType badgeType) {}
