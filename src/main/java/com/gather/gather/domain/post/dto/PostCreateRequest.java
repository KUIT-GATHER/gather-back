package com.gather.gather.domain.post.dto;

import com.gather.gather.domain.post.enums.PostType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostCreateRequest(
        @NotBlank(message = "제목은 필수입니다.") @Size(max = 15, message = "제목은 15자 이내여야 합니다.")
                String title,
        @NotBlank(message = "내용은 필수입니다.") @Size(max = 1000, message = "내용은 1000자 이내여야 합니다.")
                String content,
        @NotNull(message = "게시글 유형은 필수입니다.") PostType type,
        @Positive(message = "모집 정원은 1 이상이어야 합니다.") Integer recruitCapacity,
        @Schema(description = "presigned 업로드로 받은 이미지 objectKey 목록(선택, 최대 3장, 노출 순서)")
                @Size(max = 3, message = "이미지는 최대 3장까지 첨부할 수 있습니다.")
                List<String> imageObjectKeys) {}
