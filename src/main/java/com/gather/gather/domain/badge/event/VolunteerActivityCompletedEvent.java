package com.gather.gather.domain.badge.event;

/** 개인 봉사공고 완료 처리(PostingParticipation.complete()) 커밋 이후 뱃지 판정을 트리거하는 이벤트. */
public record VolunteerActivityCompletedEvent(Long userId) {}
