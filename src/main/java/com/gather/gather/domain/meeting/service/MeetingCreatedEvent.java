package com.gather.gather.domain.meeting.service;

/** 모임이 생성됐을 때 발행. badge 도메인이 구독해 "팀을 직접 만들기" 뱃지를 판정한다. */
public record MeetingCreatedEvent(Long userId, Long meetingId) {}
