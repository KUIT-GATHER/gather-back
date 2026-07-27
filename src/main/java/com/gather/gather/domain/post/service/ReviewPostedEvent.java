package com.gather.gather.domain.post.service;

/** 모임 게시판에 REVIEW 유형 글이 작성됐을 때 발행. badge 도메인이 구독해 "후기 작성" 뱃지를 판정한다. */
public record ReviewPostedEvent(Long userId, Long postId) {}
