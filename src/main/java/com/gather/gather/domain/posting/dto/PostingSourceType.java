package com.gather.gather.domain.posting.dto;

/** 앱 전체 봉사공고 통합 목록의 출처 구분. */
public enum PostingSourceType {
    /** 기존 앱 전체 봉사공고. */
    POSTING,
    /** external=true로 공개된 모임 내부 모집공고. */
    MEETING_RECRUIT
}
