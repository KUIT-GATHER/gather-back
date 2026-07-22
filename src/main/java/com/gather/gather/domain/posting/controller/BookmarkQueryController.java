package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.service.BookmarkService;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BookmarkQuery", description = "봉사공고 북마크 목록 조회 API")
@RestController
@RequestMapping("/api/v1/postings/bookmarks")
@RequiredArgsConstructor
public class BookmarkQueryController {

    private static final String JSON = "application/json";

    private static final String UNAUTHORIZED_EXAMPLE =
            """
            {
              "success": false,
              "data": null,
              "error": {
                "code": "UNAUTHORIZED",
                "message": "인증이 필요합니다."
              }
            }
            """;

    private final BookmarkService bookmarkService;

    @Operation(
            summary = "봉사공고 북마크 목록 조회",
            description =
                    "로그인한 사용자가 북마크한 봉사공고 목록을 북마크한 시각 최신순으로 페이지 단위 조회합니다. "
                            + "category를 지정하면 해당 봉사분야 북마크만, keyword를 지정하면 제목/모집기관명 부분일치로 "
                            + "필터링합니다. 정렬 기준은 항상 북마크한 시각 최신순으로 고정되며, sort 파라미터를 지정해 요청하면 "
                            + "400 VALIDATION_ERROR가 반환됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "북마크 목록 조회 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": {
                                                            "content": [
                                                              {
                                                                "id": 1,
                                                                "title": "동구 환경정화 봉사",
                                                                "status": "RECRUITING",
                                                                "recruitOrg": "울산 동구청",
                                                                "actStartDate": "2026-07-10",
                                                                "actEndDate": "2026-07-10",
                                                                "actPlace": "동구 일대",
                                                                "recruitCount": 5,
                                                                "applicantCount": 1,
                                                                "regionId": 2,
                                                                "regionName": "동구",
                                                                "category": "ENVIRONMENT"
                                                              }
                                                            ],
                                                            "totalElements": 1,
                                                            "totalPages": 1,
                                                            "page": 0,
                                                            "size": 20
                                                          },
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "sort 파라미터를 지정한 요청 (지원하지 않음)",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "VALIDATION_ERROR",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "VALIDATION_ERROR",
                                                            "message": "요청 값이 올바르지 않습니다."
                                                          }
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증되지 않은 요청",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "UNAUTHORIZED",
                                                value = UNAUTHORIZED_EXAMPLE)))
    })
    @GetMapping
    public ApiResponse<PageResponse<PostingSummaryResponse>> getBookmarkedPostings(
            @PageableDefault(size = 20) Pageable pageable,
            @Parameter(description = "봉사분야 카테고리 (미지정 시 전체)") @RequestParam(required = false)
                    PostingCategory category,
            @Parameter(description = "검색 키워드 (제목/모집기관명 부분일치)") @RequestParam(required = false)
                    String keyword) {
        return ApiResponse.success(
                bookmarkService.getBookmarkedPostings(category, keyword, pageable));
    }
}
