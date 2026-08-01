package com.gather.gather.domain.badge.event;

/** 모임 완료 처리(Meeting.complete()) 커밋 이후 승인된 멤버별 뱃지 판정을 트리거하는 이벤트. */
public record MeetingCompletedEvent(Long meetingId) {}
