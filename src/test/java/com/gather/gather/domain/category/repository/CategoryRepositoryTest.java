package com.gather.gather.domain.category.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.category.entity.Category;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code V5__insert_category_seed_data.sql}로 실제 시딩된 16개 봉사분야코드 row를 대상으로 한 검증. {@code
 * Category.code}가 한때 6개짜리 enum(CategoryCode)에 매핑되어 있어 시드데이터(예: "0100")를 읽는 순간 변환 예외가 났던 회귀를 방지한다.
 */
@SpringBootTest
@Transactional
class CategoryRepositoryTest {

    @Autowired private CategoryRepository categoryRepository;

    @Test
    @DisplayName("findAllByOrderByIdAsc reads all 16 seeded categories without a mapping exception")
    void findAllByOrderByIdAsc_readsAllSeededCategories() {
        List<Category> categories = categoryRepository.findAllByOrderByIdAsc();

        assertThat(categories).hasSizeGreaterThanOrEqualTo(16);
        assertThat(categories).extracting(Category::getCode).contains("0100", "1500", "1900");
    }

    @Test
    @DisplayName("findByName matches the seeded '생활편의' category used for srvcClCode text matching")
    void findByName_matchesSeededLivelihoodCategory() {
        Optional<Category> result = categoryRepository.findByName("생활편의");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("0100");
    }

    @Test
    @DisplayName("findByName matches the seeded '기타' fallback category used by PostingSyncService")
    void findByName_matchesSeededFallbackCategory() {
        Optional<Category> result = categoryRepository.findByName("기타");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("1500");
    }

    @Test
    @DisplayName("findByName returns empty when no category matches the given name")
    void findByName_returnsEmpty_whenNoCategoryMatches() {
        Optional<Category> result = categoryRepository.findByName("존재하지않는분야명");

        assertThat(result).isEmpty();
    }
}
