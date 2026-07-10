package com.gather.gather.domain.post.dto;

import com.gather.gather.domain.post.enums.PostType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @NotBlank(message = "제목은 필수입니다.") @Size(max = 255, message = "제목은 255자 이내여야 합니다.")
        String title,
        @NotBlank(message = "내용은 필수입니다.") String content,
        @NotNull(message = "게시글 유형은 필수입니다.") PostType type,
        @Positive(message = "모집 정원은 1 이상이어야 합니다.") Integer recruitCapacity) {}