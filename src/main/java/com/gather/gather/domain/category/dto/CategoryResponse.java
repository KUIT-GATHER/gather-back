package com.gather.gather.domain.category.dto;

import com.gather.gather.domain.category.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관심 카테고리 응답")
public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "1") Long id,
        @Schema(description = "1365 봉사분야코드(16종)", example = "0100") String code,
        @Schema(description = "카테고리 표시명", example = "생활편의") String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getName());
    }
}
