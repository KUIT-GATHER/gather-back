package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingResponse;
import com.gather.gather.domain.meeting.service.MeetingBookmarkService;
import com.gather.gather.domain.posting.entity.PostingCategory;
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
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MeetingBookmarkQuery", description = "관심모임 북마크 목록 조회 API")
@RestController
@RequestMapping("/api/v1/meetings/bookmarks")
@RequiredArgsConstructor
public class MeetingBookmarkQueryController {

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

    private final MeetingBookmarkService meetingBookmarkService;

    @Operation(
            summary = "관심모임 북마크 목록 조회",
            description =
                    "로그인한 사용자가 북마크한 모임 목록을 북마크한 시각 최신순으로 페이지 단위 조회합니다. "
                            + "category를 지정하면 해당 분야 북마크만, keyword를 지정하면 모임명/설명 부분일치로 "
                            + "필터링합니다. regionId는 상위 지역(시/도) 선택 시 하위 시군구 모임까지 포함합니다. "
                            + "activityStartDate/activityEndDate는 선택 기간과 모임 활동기간이 겹치는 모임을 "
                            + "조회하며, 활동 기간이 정해지지 않은 자유 모임은 날짜 필터 사용 시 제외됩니다. "
                            + "정렬 기준은 항상 북마크한 시각 최신순으로 고정되며, sort 파라미터를 지정해 요청하면 "
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
                                                                "meetingId": 1,
                                                                "name": "동구 환경정화 모임",
                                                                "description": "설명",
                                                                "currentMemberCount": 3,
                                                                "maxMember": 10,
                                                                "regionId": 2,
                                                                "categories": ["ENVIRONMENT"],
                                                                "status": "RECRUITING",
                                                                "deadline": "2026-08-01T00:00:00",
                                                                "activityStartAt": "2026-08-05T10:00:00"
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
    public ApiResponse<PageResponse<MeetingResponse>> getBookmarkedMeetings(
            @PageableDefault(size = 20) Pageable pageable,
            @Parameter(description = "봉사분야 카테고리 (미지정 시 전체)") @RequestParam(required = false)
                    PostingCategory category,
            @Parameter(description = "검색 키워드 (모임명/설명 부분일치)") @RequestParam(required = false)
                    String keyword,
            @Parameter(description = "모임 지역 ID (상위 시/도 선택 시 하위 시군구 모임 포함)")
                    @RequestParam(required = false)
                    Long regionId,
            @Parameter(description = "활동 기간 시작일 (yyyy-MM-dd)") @RequestParam(required = false)
                    LocalDate activityStartDate,
            @Parameter(description = "활동 기간 종료일 (yyyy-MM-dd)") @RequestParam(required = false)
                    LocalDate activityEndDate) {
        return ApiResponse.success(
                meetingBookmarkService.getBookmarkedMeetings(
                        category, keyword, regionId, activityStartDate, activityEndDate, pageable));
    }
}
