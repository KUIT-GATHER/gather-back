package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.dto.PostingKeywordAggregationResponse;
import com.gather.gather.domain.posting.service.PostingKeywordRecommendationService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Posting Keyword", description = "추천검색어 관리자 API (ADMIN 권한 필요)")
@RestController
@RequestMapping("/api/v1/admin/postings/keywords")
@RequiredArgsConstructor
public class AdminPostingKeywordController {

    private final PostingKeywordRecommendationService postingKeywordRecommendationService;

    @Operation(
            summary = "추천검색어 즉시 집계",
            description =
                    "매일 새벽 5시 배치(PostingKeywordAggregationScheduler)를 기다리지 않고 추천검색어 집계를 즉시 실행합니다. "
                            + "ADMIN 권한이 없으면 403 FORBIDDEN이 반환됩니다.")
    @PostMapping("/aggregate")
    public ApiResponse<PostingKeywordAggregationResponse> aggregateKeywords() {
        int count = postingKeywordRecommendationService.aggregate();
        return ApiResponse.success(new PostingKeywordAggregationResponse(count));
    }
}
