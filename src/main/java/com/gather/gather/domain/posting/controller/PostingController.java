package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.service.PostingService;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Posting", description = "봉사공고 조회 API")
@RestController
@RequestMapping("/api/v1/postings")
@RequiredArgsConstructor
public class PostingController {

    private static final String JSON = "application/json";

    private final PostingService postingService;

    @Operation(
            summary = "봉사공고 목록 조회",
            description =
                    "모집중(RECRUITING) 상태인 봉사공고를 페이지 단위로 조회합니다. 인증이 필요 없습니다. "
                            + "지역·기간 필터는 추후 추가될 예정입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "봉사공고 목록 조회 성공",
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
                                                                "categoryId": 1,
                                                                "categoryName": "환경"
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
                responseCode = "500",
                description = "서버 내부 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "INTERNAL_SERVER_ERROR",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "INTERNAL_SERVER_ERROR",
                                                            "message": "서버 내부 오류가 발생했습니다."
                                                          }
                                                        }
                                                        """)))
    })
    @GetMapping
    public ApiResponse<PageResponse<PostingSummaryResponse>> getPostings(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.success(postingService.getPostings(pageable));
    }
}
