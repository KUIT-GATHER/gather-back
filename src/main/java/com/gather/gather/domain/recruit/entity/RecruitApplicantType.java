package com.gather.gather.domain.recruit.entity;

/** 모집공고 신청 당시 사용자 구분. */
public enum RecruitApplicantType {
    /** 신청 당시 승인된 모임원. */
    MEMBER,
    /** 신청 당시 해당 모임원이 아닌 사용자(외부 공개 모집공고에만 존재 가능). */
    EXTERNAL
}
