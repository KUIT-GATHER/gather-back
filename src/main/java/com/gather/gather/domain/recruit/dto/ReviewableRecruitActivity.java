package com.gather.gather.domain.recruit.dto;

import java.time.LocalDateTime;

/** 활동 후기 작성 가능 활동 조회용 프로젝션(JPQL 생성자 표현식). 순서·타입이 쿼리와 일치해야 한다. */
public record ReviewableRecruitActivity(
        Long postId, String title, LocalDateTime activityStartAt, LocalDateTime activityEndAt) {}
