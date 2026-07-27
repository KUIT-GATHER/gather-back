package com.gather.gather.domain.user.service;

/** 회원가입 완료 또는 프로필 수정으로 관심분야가 저장된 시점에 발행. badge 도메인이 구독한다. */
public record InterestCategoriesUpdatedEvent(Long userId, int categoryCount) {}
