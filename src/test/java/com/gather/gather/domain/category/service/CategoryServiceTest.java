package com.gather.gather.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.category.dto.CategoryResponse;
import com.gather.gather.domain.category.entity.Category;
import com.gather.gather.domain.category.repository.CategoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    @DisplayName("getCategories returns all categories mapped to response, ordered by id")
    void getCategories_returnsAllCategoriesMappedToResponse() {
        Category livelihood = categoryOf(1L, "0100", "생활편의");
        Category education = categoryOf(4L, "0400", "교육");
        when(categoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of(livelihood, education));

        List<CategoryResponse> result = categoryService.getCategories();

        assertThat(result)
                .containsExactly(
                        new CategoryResponse(1L, "0100", "생활편의"),
                        new CategoryResponse(4L, "0400", "교육"));
        verify(categoryRepository).findAllByOrderByIdAsc();
    }

    @Test
    @DisplayName("getCategories returns empty list when no categories exist")
    void getCategories_returnsEmptyList_whenNoCategoriesExist() {
        when(categoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        List<CategoryResponse> result = categoryService.getCategories();

        assertThat(result).isEmpty();
    }

    private Category categoryOf(Long id, String code, String name) {
        Category category = mock(Category.class);
        lenient().when(category.getId()).thenReturn(id);
        lenient().when(category.getCode()).thenReturn(code);
        lenient().when(category.getName()).thenReturn(name);
        return category;
    }
}
