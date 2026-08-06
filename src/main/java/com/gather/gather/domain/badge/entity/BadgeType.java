package com.gather.gather.domain.badge.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BadgeType {
    FIRST_COMPLETION("첫 봉사활동 완료", "첫 봉사활동을 완료했어요", 1),
    BOOKMARK_5("봉사공고 5개 북마크", "봉사공고를 5개 북마크했어요", 5),
    FIRST_TEAM_JOIN("팀에 처음 가입하기", "처음으로 팀에 가입했어요", 1),
    FIRST_REVIEW("봉사후기 작성하기", "첫 봉사후기를 작성했어요", 1),
    COMMENT_10("게시글에 10회 댓글달기", "게시글에 댓글을 10회 남겼어요", 10),
    COMPLETION_5("봉사 5회 완료", "봉사활동을 5회 완료했어요", 5),
    CONSECUTIVE_3_MONTHS("3달 연속봉사 참여하기", "3개월 연속으로 봉사에 참여했어요", 3),
    TEAM_CREATED("팀을 직접 만들기", "직접 팀을 만들었어요", 1);

    private final String title;
    private final String description;

    /** 뱃지 획득을 위한 목표 수치 — 진행률(currentValue/targetValue) 계산의 단일 기준값. */
    private final int targetValue;
}
