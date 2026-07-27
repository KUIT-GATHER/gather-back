package com.gather.gather.domain.meeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "모임 이미지 반영 응답")
public record MeetingImageUpdateResponse(
        @Schema(description = "공개 조회 가능한 이미지 URL 목록(노출 순서)") List<String> imageUrls) {}
