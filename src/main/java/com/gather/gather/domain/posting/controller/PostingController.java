package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.dto.PostingResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.entity.PostingStatus;
import com.gather.gather.domain.posting.service.PostingKeywordRecommendationService;
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
import java.util.List;
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
    private final PostingKeywordRecommendationService postingKeywordRecommendationService;

    @Operation(
            summary = "봉사공고 목록 조회",
            description =
                    "봉사공고를 페이지 단위로 조회합니다. 인증이 필요 없습니다. "
                            + "status를 지정하지 않으면 모집중(RECRUITING)만 반환합니다. "
                            + "regionId는 상위 지역(시/도) 선택 시 하위 지역(구/군) 공고까지 포함합니다. "
                            + "regionGroupId는 활동 지역 9버튼(서울/부산/.../경상/전라/충청) 선택 시 그 권역에 속한 "
                            + "모든 시도와 시군구 공고를 포함합니다. regionId와 regionGroupId는 동시에 지정할 수 없습니다. "
                            + "noticeStartDate/noticeEndDate는 각각 모집시작일 하한/모집종료일 상한 필터입니다. "
                            + "keyword는 제목/모집기관명 부분일치 검색입니다. "
                            + "category를 지정하면 해당 봉사분야 공고만 반환합니다(미지정 시 전체).",
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
                description = "요청 값이 올바르지 않음 (예: regionId와 regionGroupId 동시 지정, 존재하지 않는 sort 프로퍼티)",
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
            @Parameter(description = "지역 ID (상위 지역 선택 시 하위 지역까지 포함, regionGroupId와 동시 지정 불가)")
                    @RequestParam(required = false)
                    Long regionId,
            @Parameter(description = "활동 지역 권역 ID (9버튼, regionId와 동시 지정 불가)")
                    @RequestParam(required = false)
                    Long regionGroupId,
            @Parameter(description = "모집상태 (미지정 시 RECRUITING)") @RequestParam(required = false)
                    PostingStatus status,
            @Parameter(description = "모집시작일 하한 (yyyy-MM-dd)") @RequestParam(required = false)
                    LocalDate noticeStartDate,
            @Parameter(description = "모집종료일 상한 (yyyy-MM-dd)") @RequestParam(required = false)
                    LocalDate noticeEndDate,
            @Parameter(description = "검색 키워드 (제목/모집기관명 부분일치)") @RequestParam(required = false)
                    String keyword,
            @Parameter(
                            description =
                                    "봉사분야 카테고리 (ENVIRONMENT/EDUCATION/CULTURE/COMMUNITY/WELFARE/OVERSEAS,"
                                            + " 미지정 시 전체)")
                    @RequestParam(required = false)
                    PostingCategory category) {
        return ApiResponse.success(
                postingService.getPostings(
                        pageable,
                        regionId,
                        regionGroupId,
                        status,
                        noticeStartDate,
                        noticeEndDate,
                        keyword,
                        category));
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

    @Operation(
            summary = "추천검색어 목록 조회",
            description =
                    "최근 60일간 봉사공고 검색어를 형태소 분석해 집계한 인기 검색어 상위 10개를 반환합니다. "
                            + "매일 새벽 5시 배치로 갱신되며, 실시간 반영은 아닙니다. 인증이 필요 없습니다.")
    @GetMapping("/keywords/recommended")
    public ApiResponse<List<String>> getRecommendedKeywords() {
        return ApiResponse.success(postingKeywordRecommendationService.getRecommendedKeywords());
    }
}
