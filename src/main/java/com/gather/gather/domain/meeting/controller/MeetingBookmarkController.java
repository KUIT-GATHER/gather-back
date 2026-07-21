package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingBookmarkResponse;
import com.gather.gather.domain.meeting.service.MeetingBookmarkService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MeetingBookmark", description = "관심모임 북마크 API")
@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/bookmark")
@RequiredArgsConstructor
public class MeetingBookmarkController {

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

    @Operation(summary = "관심모임 북마크 추가", description = "로그인한 사용자가 모임을 북마크에 추가합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "북마크 추가 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": { "meetingId": 1, "bookmarked": true },
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
                description = "모임을 찾을 수 없음",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "MEETING_NOT_FOUND",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "MEETING_NOT_FOUND",
                                                            "message": "모임을 찾을 수 없습니다."
                                                          }
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 북마크한 모임",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "MEETING_BOOKMARK_DUPLICATE",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "MEETING_BOOKMARK_DUPLICATE",
                                                            "message": "이미 북마크한 모임입니다."
                                                          }
                                                        }
                                                        """)))
    })
    @PostMapping
    public ApiResponse<MeetingBookmarkResponse> addBookmark(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingBookmarkService.addBookmark(meetingId));
    }

    @Operation(summary = "관심모임 북마크 삭제", description = "로그인한 사용자가 모임의 북마크를 삭제합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "북마크 삭제 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": { "meetingId": 1, "bookmarked": false },
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
                description = "북마크한 적 없는 모임",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "MEETING_BOOKMARK_NOT_FOUND",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "MEETING_BOOKMARK_NOT_FOUND",
                                                            "message": "북마크를 찾을 수 없습니다."
                                                          }
                                                        }
                                                        """)))
    })
    @DeleteMapping
    public ApiResponse<MeetingBookmarkResponse> removeBookmark(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingBookmarkService.removeBookmark(meetingId));
    }
}
