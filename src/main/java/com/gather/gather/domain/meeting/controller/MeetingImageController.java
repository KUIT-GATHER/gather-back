package com.gather.gather.domain.meeting.controller;

import com.gather.gather.domain.meeting.dto.MeetingImageListResponse;
import com.gather.gather.domain.meeting.dto.MeetingImagePresignedUrlRequest;
import com.gather.gather.domain.meeting.dto.MeetingImagePresignedUrlResponse;
import com.gather.gather.domain.meeting.dto.MeetingImageUpdateRequest;
import com.gather.gather.domain.meeting.dto.MeetingImageUpdateResponse;
import com.gather.gather.domain.meeting.service.MeetingImageService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meeting Image", description = "모임 이미지 S3 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meetings/{meetingId}/images")
public class MeetingImageController {

    private final MeetingImageService meetingImageService;

    @Operation(
            summary = "모임 이미지 Presigned PUT URL 발급",
            description =
                    """
                    JPEG, PNG, WebP 형식의 최대 5MB 이미지를 업로드할 URL을 발급합니다(모임장 전용).
                    프론트는 uploadUrl로 PUT 요청을 보내며, 발급 요청과 동일한 Content-Type과
                    If-None-Match: * 헤더를 사용해야 합니다. 412 Precondition Failed 시 기존 URL을
                    재시도하지 말고 새 URL을 발급받습니다. 업로드 성공 후 objectKey를 모아 반영 API를 호출합니다.
                    """)
    @PostMapping("/presigned-url")
    public ApiResponse<MeetingImagePresignedUrlResponse> createPresignedUrl(
            @PathVariable Long meetingId,
            @Valid @RequestBody MeetingImagePresignedUrlRequest request) {
        return ApiResponse.success(meetingImageService.createPresignedUrl(meetingId, request));
    }

    @Operation(
            summary = "모임 이미지 반영",
            description = "업로드된 이미지 세트를 모임에 반영합니다(모임장 전용, 최대 3장). objectKeys 순서가 노출 순서가 됩니다.")
    @PatchMapping
    public ApiResponse<MeetingImageUpdateResponse> updateImages(
            @PathVariable Long meetingId, @Valid @RequestBody MeetingImageUpdateRequest request) {
        return ApiResponse.success(meetingImageService.updateImages(meetingId, request));
    }

    @Operation(summary = "모임 이미지 조회", description = "모임의 현재 이미지 URL 목록을 노출 순서대로 반환합니다.")
    @GetMapping
    public ApiResponse<MeetingImageListResponse> getImages(@PathVariable Long meetingId) {
        return ApiResponse.success(meetingImageService.getImages(meetingId));
    }
}