package com.gather.gather.domain.post.enums;

/** 활동 후기(REVIEW 게시글)가 어떤 활동을 근거로 작성됐는지 나타낸다. */
public enum ReviewSourceType {
    /** 앱 전체 봉사공고(Posting) 참여. */
    POSTING,
    /** 모임 내부 모집공고(MeetingRecruit) 참여. */
    MEETING_RECRUIT
}
