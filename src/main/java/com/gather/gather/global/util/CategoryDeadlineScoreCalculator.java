package com.gather.gather.global.util;

import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.config.RecommendationProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 봉사공고/모임 추천 점수 산식(카테고리 매칭 + 마감일 근접도, 기본 가중치 0.7:0.3).
 *
 * <p>봉사공고와 모임 양쪽 추천 서비스가 동일한 산식을 공유하도록 이 유틸에만 로직을 둔다. 가중치가 바뀌면 이 클래스 하나만 수정하면 된다.
 */
@Component
@RequiredArgsConstructor
public class CategoryDeadlineScoreCalculator {

    private final RecommendationProperties recommendationProperties;

    /**
     * @param category 대상(봉사공고/모임)의 카테고리
     * @param preferredCategories 사용자의 선호 카테고리(비로그인·미설정이면 빈 Set)
     * @param deadline 마감 시각(null이면 근접도 점수 0)
     * @param now 기준 시각
     */
    public double score(
            PostingCategory category,
            Set<PostingCategory> preferredCategories,
            LocalDateTime deadline,
            LocalDateTime now) {
        double categoryScore = preferredCategories.contains(category) ? 1.0 : 0.0;
        double deadlineScore = deadlineProximityScore(deadline, now);
        return recommendationProperties.categoryWeight() * categoryScore
                + recommendationProperties.deadlineWeight() * deadlineScore;
    }

    private double deadlineProximityScore(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null) {
            return 0.0;
        }
        long daysUntilDeadline = Duration.between(now, deadline).toDays();
        int windowDays = recommendationProperties.deadlineWindowDays();
        double raw = (windowDays - daysUntilDeadline) / (double) windowDays;
        return Math.max(0.0, Math.min(1.0, raw));
    }
}
