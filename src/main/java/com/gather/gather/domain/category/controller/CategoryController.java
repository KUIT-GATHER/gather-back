package com.gather.gather.domain.category.controller;

import com.gather.gather.domain.category.dto.CategoryResponse;
import com.gather.gather.domain.category.service.CategoryService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Signup Support", description = "회원가입 보조 조회 API")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(
            summary = "관심 카테고리 목록 조회",
            description = "회원가입 프로필 단계에서 선택 가능한 관심 카테고리 목록을 조회합니다. 인증이 필요 없습니다.")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(categoryService.getCategories());
    }
}
