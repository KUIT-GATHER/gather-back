package com.gather.gather.domain.mypage.controller;

import com.gather.gather.domain.mypage.dto.MyPageActivityResponse;
import com.gather.gather.domain.mypage.dto.MyPageHomeResponse;
import com.gather.gather.domain.mypage.service.MyPageService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MyPage", description = "마이페이지 홈/활동 캘린더 API")
@RestController
@RequestMapping("/api/v1/mypage")
@RequiredArgsConstructor
public class MyPageController {

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

    private final MyPageService myPageService;

    @Operation(summary = "마이페이지 홈 조회", description = "프로필 요약(사진/닉네임/생년월일/지역)과 북마크 보유 여부를 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "마이페이지 홈 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증되지 않은 요청",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "UNAUTHORIZED",
                                                value = UNAUTHORIZED_EXAMPLE))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "USER_NOT_FOUND",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "USER_NOT_FOUND",
                                                            "message": "사용자를 찾을 수 없습니다."
                                                          }
                                                        }
                                                        """)))
    })
    @GetMapping("/home")
    public ApiResponse<MyPageHomeResponse> getHome() {
        return ApiResponse.success(myPageService.getHome());
    }

    @Operation(summary = "마이페이지 활동 캘린더 조회", description = "지정한 월의 봉사 참여 일정 목록(일정카드)을 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "활동 캘린더 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "yearMonth 누락 또는 형식 오류(yyyy-MM)",
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
    @GetMapping("/activities")
    public ApiResponse<List<MyPageActivityResponse>> getActivities(
            @Parameter(description = "조회할 연월 (yyyy-MM)", example = "2026-07")
                    @RequestParam
                    @DateTimeFormat(pattern = "yyyy-MM")
                    YearMonth yearMonth) {
        return ApiResponse.success(myPageService.getActivities(yearMonth));
    }
}
