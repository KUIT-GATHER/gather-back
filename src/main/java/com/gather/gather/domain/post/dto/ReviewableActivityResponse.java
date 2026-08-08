package com.gather.gather.domain.post.dto;

import com.gather.gather.domain.post.enums.ReviewSourceType;
import java.time.LocalDateTime;

/** 후기 작성 가능 활동 카드. 사용자가 이 중 하나를 골라 REVIEW 게시글 작성 시 reviewSourceType/reviewSourceId로 전달한다. */
public record ReviewableActivityResponse(
        ReviewSourceType reviewSourceType,
        Long reviewSourceId,
        String title,
        LocalDateTime activityStartAt,
        LocalDateTime activityEndAt) {}
