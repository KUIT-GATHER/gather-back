package com.gather.gather.domain.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostUpdateRequest(
        @NotBlank(message = "제목은 필수입니다.") @Size(max = 255, message = "제목은 255자 이내여야 합니다.")
                String title,
        @NotBlank(message = "내용은 필수입니다.") String content,
        @Schema(description = "이미지 objectKey 목록(노출 순서). null이면 이미지 변경 없음, 빈 배열이면 전체 제거. 최대 3장.")
                @Size(max = 3, message = "이미지는 최대 3장까지 첨부할 수 있습니다.")
                List<String> imageObjectKeys) {}
