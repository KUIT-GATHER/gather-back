package com.gather.gather.domain.meeting.service;

/**
 * 모임 가입 신청이 APPROVED로 확정됐을 때 발행. badge 도메인이 구독해 가입자의 "팀에 처음 가입하기"와 모임장(hostUserId)의 "팀원 모집 성공"을 함께
 * 판정한다.
 */
public record MeetingJoinedEvent(Long joinedUserId, Long hostUserId, Long meetingId) {}
