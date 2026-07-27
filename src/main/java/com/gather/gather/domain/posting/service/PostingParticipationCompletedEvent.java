package com.gather.gather.domain.posting.service;

/** 봉사 참여가 COMPLETED로 전이됐을 때 발행. badge 도메인이 구독해 관련 뱃지 달성 여부를 판정한다. */
public record PostingParticipationCompletedEvent(Long userId, Long postingId) {}
