package com.gather.gather.domain.recruit.entity;

/**
 * 모집공고 참여신청 상태.
 *
 * <p>{@code APPLIED}(신청 완료) → {@code COMPLETED}(봉사 완료). 봉사 완료는 모임장이 모임을 완료 처리할 때 그 모임의 모집공고 참여에 일괄
 * 반영된다. 후기작성 완료(REVIEWED) 상태는 후기↔참여활동 연결 기능과 함께 후속에 추가한다.
 */
public enum MeetingRecruitParticipationStatus {
    APPLIED,
    COMPLETED
}
