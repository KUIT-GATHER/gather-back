package com.gather.gather.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.config.RecommendationProperties;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryDeadlineScoreCalculatorTest {

    // category-weight=0.7, deadline-weight=0.3, deadline-window-days=30
    private final CategoryDeadlineScoreCalculator calculator =
            new CategoryDeadlineScoreCalculator(new RecommendationProperties(0.7, 0.3, 30));

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 28, 0, 0);

    @Test
    @DisplayName(
            "score is category weight + full deadline weight when category matches and deadline is today")
    void score_matchedCategoryAndDeadlineToday_returnsFullScore() {
        double score =
                calculator.score(
                        PostingCategory.ENVIRONMENT, Set.of(PostingCategory.ENVIRONMENT), now, now);

        assertThat(score).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("score is zero when category doesn't match and deadline is far beyond the window")
    void score_unmatchedCategoryAndFarDeadline_returnsZero() {
        double score =
                calculator.score(
                        PostingCategory.ENVIRONMENT,
                        Set.of(PostingCategory.WELFARE),
                        now.plusDays(60),
                        now);

        assertThat(score).isCloseTo(0.0, within(0.0001));
    }

    @Test
    @DisplayName(
            "score reflects only category weight when preferred categories is empty (guest/no-preference fallback)")
    void score_emptyPreferredCategories_onlyDeadlineContributes() {
        double score =
                calculator.score(PostingCategory.ENVIRONMENT, Set.of(), now.plusDays(15), now);

        // deadlineScore = (30 - 15) / 30 = 0.5 → 0.3 * 0.5 = 0.15
        assertThat(score).isCloseTo(0.15, within(0.0001));
    }

    @Test
    @DisplayName("score treats deadline further than the window as zero deadline proximity")
    void score_deadlineBeyondWindow_deadlineScoreClampedToZero() {
        double score =
                calculator.score(
                        PostingCategory.ENVIRONMENT,
                        Set.of(PostingCategory.ENVIRONMENT),
                        now.plusDays(45),
                        now);

        // categoryScore=1.0 → 0.7, deadlineScore clamped to 0
        assertThat(score).isCloseTo(0.7, within(0.0001));
    }

    @Test
    @DisplayName("score treats a null deadline as zero deadline proximity without throwing")
    void score_nullDeadline_onlyCategoryContributes() {
        double score =
                calculator.score(
                        PostingCategory.ENVIRONMENT,
                        Set.of(PostingCategory.ENVIRONMENT),
                        null,
                        now);

        assertThat(score).isCloseTo(0.7, within(0.0001));
    }

    @Test
    @DisplayName("nearer deadlines score higher than farther ones when category match is equal")
    void score_nearerDeadline_scoresHigherThanFartherDeadline() {
        double nearScore =
                calculator.score(PostingCategory.ENVIRONMENT, Set.of(), now.plusDays(1), now);
        double farScore =
                calculator.score(PostingCategory.ENVIRONMENT, Set.of(), now.plusDays(20), now);

        assertThat(nearScore).isGreaterThan(farScore);
    }

    @Test
    @DisplayName(
            "score treats a deadline exactly at the window boundary as zero deadline proximity")
    void score_deadlineExactlyAtWindowBoundary_deadlineScoreIsZero() {
        double score =
                calculator.score(PostingCategory.ENVIRONMENT, Set.of(), now.plusDays(30), now);

        // daysUntilDeadline == windowDays → raw = (30 - 30) / 30 = 0
        assertThat(score).isCloseTo(0.0, within(0.0001));
    }

    @Test
    @DisplayName("score ignores category match entirely when categoryWeight is configured as zero")
    void score_zeroCategoryWeight_categoryMatchDoesNotContribute() {
        CategoryDeadlineScoreCalculator zeroCategoryWeightCalculator =
                new CategoryDeadlineScoreCalculator(new RecommendationProperties(0.0, 0.3, 30));

        double matchedScore =
                zeroCategoryWeightCalculator.score(
                        PostingCategory.ENVIRONMENT,
                        Set.of(PostingCategory.ENVIRONMENT),
                        now.plusDays(15),
                        now);
        double unmatchedScore =
                zeroCategoryWeightCalculator.score(
                        PostingCategory.ENVIRONMENT,
                        Set.of(PostingCategory.WELFARE),
                        now.plusDays(15),
                        now);

        assertThat(matchedScore).isCloseTo(unmatchedScore, within(0.0001));
    }

    @Test
    @DisplayName(
            "score ignores deadline proximity entirely when deadlineWeight is configured as zero")
    void score_zeroDeadlineWeight_deadlineProximityDoesNotContribute() {
        CategoryDeadlineScoreCalculator zeroDeadlineWeightCalculator =
                new CategoryDeadlineScoreCalculator(new RecommendationProperties(0.7, 0.0, 30));

        double nearScore =
                zeroDeadlineWeightCalculator.score(
                        PostingCategory.ENVIRONMENT,
                        Set.of(PostingCategory.ENVIRONMENT),
                        now.plusDays(1),
                        now);
        double farScore =
                zeroDeadlineWeightCalculator.score(
                        PostingCategory.ENVIRONMENT,
                        Set.of(PostingCategory.ENVIRONMENT),
                        now.plusDays(20),
                        now);

        assertThat(nearScore).isCloseTo(farScore, within(0.0001));
    }
}
