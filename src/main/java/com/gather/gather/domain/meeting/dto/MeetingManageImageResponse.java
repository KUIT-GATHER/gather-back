package com.gather.gather.domain.meeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "모임 이미지 관리 정보(수정 화면용) - objectKey를 포함한다")
public record MeetingManageImageResponse(
        @Schema(description = "S3 객체 키") String objectKey,
        @Schema(description = "공개 조회 URL") String imageUrl,
        @Schema(description = "노출 순서(0부터)") int sortOrder) {}
