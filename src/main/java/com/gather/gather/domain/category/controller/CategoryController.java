package com.gather.gather.domain.category.controller;

import com.gather.gather.domain.category.dto.CategoryResponse;
import com.gather.gather.domain.category.service.CategoryService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    private static final String JSON = "application/json";

    private final CategoryService categoryService;

    @Operation(
            summary = "관심 카테고리 목록 조회",
            description = "회원가입 프로필 단계에서 선택 가능한 관심 카테고리 목록을 조회합니다. 인증이 필요 없습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "관심 카테고리 목록 조회 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": [
                                                            {
                                                              "id": 1,
                                                              "code": "ENVIRONMENT",
                                                              "name": "환경"
                                                            }
                                                          ],
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "INTERNAL_SERVER_ERROR",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "INTERNAL_SERVER_ERROR",
                                                            "message": "서버 내부 오류가 발생했습니다."
                                                          }
                                                        }
                                                        """)))
    })
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(categoryService.getCategories());
    }
}
