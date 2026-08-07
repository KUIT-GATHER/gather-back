package com.gather.gather.domain.meeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "모임 이미지 반영 요청. 새 업로드 key와 유지할 기존 key를 노출 순서대로 담는다(최대 3장). 빈 배열은 전체 삭제로 처리한다.")
public record MeetingImageUpdateRequest(
        @Schema(description = "반영할 S3 객체 키 목록(순서 = 노출 순서). 빈 배열이면 전체 이미지를 삭제한다.")
                @NotNull
                @Size(max = 3)
                List<String> objectKeys) {}
