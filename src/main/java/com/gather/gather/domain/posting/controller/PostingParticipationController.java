package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.dto.PostingParticipationResponse;
import com.gather.gather.domain.posting.service.PostingParticipationService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "PostingParticipation", description = "봉사공고 참여(신청) API")
@RestController
@RequestMapping("/api/v1/postings/{postingId}/participations")
@RequiredArgsConstructor
public class PostingParticipationController {

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

    private final PostingParticipationService postingParticipationService;

    @Operation(
            summary = "봉사 신청",
            description = "로그인한 사용자가 봉사공고에 신청한다. 신청 내역을 저장하고 1365 신청 링크를 함께 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "봉사 신청 성공",
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
                                                            "participationId": 1,
                                                            "status": "APPLIED",
                                                            "applicationUrl": "https://1365.go.kr/vols/P9210/partcptn/timeCptn.do?type=show&progrmRegistNo=3422497"
                                                          },
                                                          "error": null
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
                                                value = UNAUTHORIZED_EXAMPLE))),
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
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "마감된 공고 / 이미 신청한 공고 / 1365 신청 정보가 연동되지 않은 공고",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "POSTING_CLOSED",
                                            value =
                                                    """
                                                    {
                                                      "success": false,
                                                      "data": null,
                                                      "error": {
                                                        "code": "POSTING_CLOSED",
                                                        "message": "마감된 봉사공고입니다."
                                                      }
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "PARTICIPATION_DUPLICATE",
                                            value =
                                                    """
                                                    {
                                                      "success": false,
                                                      "data": null,
                                                      "error": {
                                                        "code": "PARTICIPATION_DUPLICATE",
                                                        "message": "이미 신청한 봉사입니다."
                                                      }
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "POSTING_APPLICATION_UNAVAILABLE",
                                            value =
                                                    """
                                                    {
                                                      "success": false,
                                                      "data": null,
                                                      "error": {
                                                        "code": "POSTING_APPLICATION_UNAVAILABLE",
                                                        "message": "1365 신청 정보가 연동되지 않아 신청할 수 없는 공고입니다."
                                                      }
                                                    }
                                                    """)
                                }))
    })
    @PostMapping
    public ApiResponse<PostingParticipationResponse> apply(@PathVariable Long postingId) {
        return ApiResponse.success(postingParticipationService.apply(postingId));
    }
}
