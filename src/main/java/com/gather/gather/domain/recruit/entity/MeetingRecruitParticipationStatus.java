package com.gather.gather.domain.recruit.entity;

/**
 * 모집공고 참여신청 상태.
 *
 * <p>현재는 신청/취소만 다루므로 {@code APPLIED} 하나만 둔다. 봉사완료·후기작성 등 라이프사이클 상태는 모임 완료/후기 이벤트에서 파생하도록 후속에 추가한다.
 */
public enum MeetingRecruitParticipationStatus {
    APPLIED
}
