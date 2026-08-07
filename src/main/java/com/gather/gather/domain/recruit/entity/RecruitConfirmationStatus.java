package com.gather.gather.domain.recruit.entity;

/** 모집공고 참가자 확정 상태. 확정 후에는 신규 신청·취소가 불가능하다. */
public enum RecruitConfirmationStatus {
    UNCONFIRMED,
    CONFIRMED
}
