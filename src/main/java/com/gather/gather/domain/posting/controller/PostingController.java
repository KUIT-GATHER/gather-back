package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.service.PostingService;
import com.gather.gather.global.common.ApiResponse;
import com.gather.gather.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
                    "봉사공고를 페이지 단위로 조회합니다. 인증이 필요 없습니다. "
                            + "status를 지정하지 않으면 모집중(RECRUITING)만 반환합니다. "
                            + "regionId는 상위 지역(시/도) 선택 시 하위 지역(구/군) 공고까지 포함합니다. "
                            + "noticeStartDate/noticeEndDate는 각각 모집시작일 하한/모집종료일 상한 필터입니다. "
                            + "keyword는 제목/모집기관명 부분일치 검색입니다.",
            parameters = {
                @Parameter(
                        name = "sort",
                        description =
                                "정렬 기준 (property,direction). 예: id,desc. "
                                        + "허용 필드: id, title, status, actStartDate, actEndDate, "
                                        + "noticeStartDate, noticeEndDate, recruitCount, applicantCount, "
                                        + "createdAt, updatedAt. 허용되지 않은 필드로 정렬을 요청하면 400 "
                                        + "VALIDATION_ERROR가 반환됩니다.",
                        example = "id,desc")
            })
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
                responseCode = "400",
                description = "허용되지 않은 sort 프로퍼티 등 잘못된 요청 값",
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
                    Pageable pageable,
            @Parameter(description = "지역 ID (상위 지역 선택 시 하위 지역까지 포함)")
                    @RequestParam(required = false)
                    Long regionId,
            @Parameter(description = "모집상태 (미지정 시 RECRUITING)") @RequestParam(required = false)
                    PostingStatus status,
            @Parameter(description = "모집시작일 하한 (yyyy-MM-dd)") @RequestParam(required = false)
                    LocalDate noticeStartDate,
            @Parameter(description = "모집종료일 상한 (yyyy-MM-dd)") @RequestParam(required = false)
                    LocalDate noticeEndDate,
            @Parameter(description = "검색 키워드 (제목/모집기관명 부분일치)") @RequestParam(required = false)
                    String keyword) {
        return ApiResponse.success(
                postingService.getPostings(
                        pageable, regionId, status, noticeStartDate, noticeEndDate, keyword));
    }

    @Operation(summary = "봉사공고 상세 조회", description = "봉사공고 상세 정보를 조회합니다. 인증이 필요 없습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "봉사공고 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "봉사공고를 찾을 수 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "POSTING_NOT_FOUND",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "POSTING_NOT_FOUND",
                                                            "message": "봉사공고를 찾을 수 없습니다."
                                                          }
                                                        }
                                                        """)))
    })
    @GetMapping("/{id}")
    public ApiResponse<PostingResponse> getPosting(@PathVariable Long id) {
        return ApiResponse.success(postingService.getPosting(id));
    }
}
