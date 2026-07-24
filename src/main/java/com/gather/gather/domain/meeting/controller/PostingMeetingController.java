package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.PostingMeetingResponse;
import com.gather.gather.domain.meeting.service.MeetingService;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting", description = "모임 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/postings/{postingId}/meetings")
public class PostingMeetingController {

    private final MeetingService meetingService;

    @Operation(
            summary = "봉사공고 기반 모임 목록 조회",
            description = "특정 봉사공고를 기반으로 생성된 모임 목록을 페이지 단위로 조회합니다. " + "삭제된 모임은 제외하며 인증이 필요 없습니다.",
            parameters = {
                @Parameter(
                        name = "sort",
                        description =
                                "정렬 기준 (property,direction). 기본값은 createdAt,desc입니다. "
                                        + "예: createdAt,desc",
                        example = "createdAt,desc")
            })
    @GetMapping
    public ApiResponse<PageResponse<PostingMeetingResponse>> getMeetingsByPosting(
            @PathVariable Long postingId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.success(meetingService.getMeetingsByPosting(postingId, pageable));
    }
}
