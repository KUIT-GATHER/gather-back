package com.gather.gather.domain.category.dto;

import com.gather.gather.domain.category.entity.Category;
import com.gather.gather.domain.category.entity.CategoryCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관심 카테고리 응답")
public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "1") Long id,
        @Schema(description = "프론트 아이콘/색상 매핑용 고정 코드", example = "ENVIRONMENT")
                CategoryCode code,
        @Schema(description = "카테고리 표시명", example = "환경") String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName());
    }
}
