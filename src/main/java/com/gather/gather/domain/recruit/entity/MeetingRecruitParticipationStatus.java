package com.gather.gather.domain.recruit.entity;

/**
 * 모집공고 참여신청 상태.
 *
 * <p>{@code APPLIED}(신청) → {@code CONFIRMED}(확정, 팀장이 일괄 확정) → {@code COMPLETED}(출석 처리 완료) → {@code
 * REVIEWED}(후기 작성됨). {@code CANCELLED}는 신청자 본인이 취소한 상태로 마감 전 재신청 가능, {@code REJECTED}는 팀장이 반려한 상태로
 * 재신청 불가.
 */
public enum MeetingRecruitParticipationStatus {
    APPLIED,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    COMPLETED,
    REVIEWED
}
