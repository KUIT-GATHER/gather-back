package com.gather.gather.domain.meeting.enums;

public enum MeetingMemberStatus {
    PENDING,
    APPROVED,
    REJECTED,
    LEFT,
    /** 대기 중이던 사용자가 본인 가입 신청을 직접 취소한 상태. 호스트가 거절한 REJECTED와는 구분한다. */
    CANCELLED
}
