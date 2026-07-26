package com.gather.gather.domain.meeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "모임 이미지 반영 요청. 새 업로드 key와 유지할 기존 key를 노출 순서대로 담는다(최대 3장).")
public record MeetingImageUpdateRequest(
        @Schema(description = "반영할 S3 객체 키 목록(순서 = 노출 순서)")
        @NotEmpty
        @Size(max = 3)
        List<String> objectKeys) {}