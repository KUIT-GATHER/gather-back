package com.gather.gather.domain.recruit.entity;

/**
 * 모집공고 참여신청 상태.
 *
 * <p>{@code APPLIED}(신청 완료) → {@code COMPLETED}(봉사 완료). 봉사 완료는 모임장이 모임을 완료 처리할 때 그 모임의 모집공고 참여에 일괄
 * 반영된다.
 *
 * <p>{@code CONFIRMED}·{@code REVIEWED}는 {@code PostingParticipationStatus}와 동일한 컨벤션으로 미리 선언해 둔 예약값이다.
 * 현재 이 상태로 전환하는 로직은 없으며(참여 승인 단계·후기 기능 모두 아직 없음), 각 기능이 추가될 때 채운다. 프론트는 값이 늘어날 것을 감안해 상태를 표시할 때 {@code
 * APPLIED}/{@code CONFIRMED} → "신청중", {@code COMPLETED}/{@code REVIEWED} → "봉사 완료"로 두 그룹으로 묶어 처리하는 것을 권장한다.
 */
public enum MeetingRecruitParticipationStatus {
    APPLIED,
    CONFIRMED,
    COMPLETED,
    REVIEWED
}