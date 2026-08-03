package com.gather.gather.domain.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 나의 활동 탭 요약(모임 내부). 각 항목 클릭 시 해당 목록 API로 이동한다. */
public record MyMeetingActivitySummaryResponse(
        @Schema(description = "내가 이 모임에서 작성한 게시글 수", example = "1") long writtenPostCount,
        @Schema(description = "내가 이 모임에서 댓글을 단 게시글 수", example = "1") long commentedPostCount,
        @Schema(description = "내가 이 모임에서 신청한 봉사(모집공고) 수", example = "1")
                long appliedRecruitCount) {}
