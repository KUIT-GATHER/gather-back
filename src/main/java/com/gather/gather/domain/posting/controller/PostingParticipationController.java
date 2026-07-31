package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.dto.PostingParticipationResponse;
import com.gather.gather.domain.posting.dto.PostingRecognizedMinutesRequest;
import com.gather.gather.domain.posting.service.PostingParticipationService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(summary = "봉사 신청 취소", description = "로그인한 사용자가 자신의 봉사 신청 내역을 취소(삭제)한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "봉사 신청 취소 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": null,
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
                description = "신청 내역을 찾을 수 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PARTICIPATION_NOT_FOUND",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "PARTICIPATION_NOT_FOUND",
                                                            "message": "신청 내역을 찾을 수 없습니다."
                                                          }
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이력 보존을 위해 취소가 금지된 상태(완료/후기작성)",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PARTICIPATION_CANCEL_NOT_ALLOWED",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "PARTICIPATION_CANCEL_NOT_ALLOWED",
                                                            "message": "이력 보존을 위해 완료되었거나 후기가 작성된 신청은 취소할 수 없습니다."
                                                          }
                                                        }
                                                        """)))
    })
    @DeleteMapping
    public ApiResponse<Void> cancel(@PathVariable Long postingId) {
        postingParticipationService.cancel(postingId);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "개인 봉사 완료 처리",
            description = "활동종료일이 지난 뒤 본인이 직접 완료 처리한다. 모임(그룹) 봉사는 모임장이 별도 API로 완료 처리한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "완료 처리 성공"),
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
                description = "신청 내역을 찾을 수 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "PARTICIPATION_NOT_FOUND",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "PARTICIPATION_NOT_FOUND",
                                                            "message": "신청 내역을 찾을 수 없습니다."
                                                          }
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 완료됨 / 활동종료일 미경과",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "PARTICIPATION_ALREADY_COMPLETED",
                                            value =
                                                    """
                                                    {
                                                      "success": false,
                                                      "data": null,
                                                      "error": {
                                                        "code": "PARTICIPATION_ALREADY_COMPLETED",
                                                        "message": "이미 완료 처리된 참여입니다."
                                                      }
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "PARTICIPATION_COMPLETE_NOT_ALLOWED",
                                            value =
                                                    """
                                                    {
                                                      "success": false,
                                                      "data": null,
                                                      "error": {
                                                        "code": "PARTICIPATION_COMPLETE_NOT_ALLOWED",
                                                        "message": "활동종료일이 지나야 완료 처리를 할 수 있습니다."
                                                      }
                                                    }
                                                    """)
                                }))
    })
    @PatchMapping("/complete")
    public ApiResponse<Void> complete(@PathVariable Long postingId) {
        postingParticipationService.complete(postingId);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "봉사 인정시간 입력",
            description = "완료 처리된 참여에 한해, 본인이 직접 인정시간(분 단위, 10분 단위)을 입력한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "인정시간 입력 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "인정시간 형식 오류(10분 단위·양수·상한 이내가 아님, VALIDATION_ERROR)"),
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
                responseCode = "409",
                description = "완료 처리되지 않음 / 이미 입력됨",
                content =
                        @Content(
                                mediaType = JSON,
                                examples = {
                                    @ExampleObject(
                                            name = "PARTICIPATION_HOURS_NOT_ALLOWED",
                                            value =
                                                    """
                                                    {
                                                      "success": false,
                                                      "data": null,
                                                      "error": {
                                                        "code": "PARTICIPATION_HOURS_NOT_ALLOWED",
                                                        "message": "완료 처리된 참여만 인정시간을 입력할 수 있습니다."
                                                      }
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "PARTICIPATION_HOURS_ALREADY_SUBMITTED",
                                            value =
                                                    """
                                                    {
                                                      "success": false,
                                                      "data": null,
                                                      "error": {
                                                        "code": "PARTICIPATION_HOURS_ALREADY_SUBMITTED",
                                                        "message": "이미 인정시간을 입력했습니다."
                                                      }
                                                    }
                                                    """)
                                }))
    })
    @PatchMapping("/hours")
    public ApiResponse<Void> submitRecognizedMinutes(
            @PathVariable Long postingId, @RequestBody PostingRecognizedMinutesRequest request) {
        postingParticipationService.submitRecognizedMinutes(postingId, request.recognizedMinutes());
        return ApiResponse.success(null);
    }
}
