package com.gather.gather.domain.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 좋아요 토글 결과. 프론트는 이 값으로 하트 상태와 카운트를 갱신한다. */
public record PostLikeResponse(
        @Schema(description = "토글 후 조회자의 좋아요 여부", example = "true") boolean liked,
        @Schema(description = "토글 후 게시글의 총 좋아요 수", example = "11") int likeCount) {}
