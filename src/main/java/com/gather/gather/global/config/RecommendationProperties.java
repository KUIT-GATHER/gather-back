package com.gather.gather.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 봉사공고·모임 추천 점수 산식 가중치. {@code @ConfigurationPropertiesScan}으로 자동 등록된다.
 *
 * <p>categoryWeight/deadlineWeight는 카테고리 매칭 점수와 마감일 근접도 점수에 각각 곱해지는 가중치이고, deadlineWindowDays는 마감일
 * 근접도 점수를 0~1로 정규화하는 기준 일수다(마감까지 이 일수 이상 남으면 근접도 점수 0).
 */
@ConfigurationProperties(prefix = "gather.recommendation")
public record RecommendationProperties(
        @DefaultValue("0.7") double categoryWeight,
        @DefaultValue("0.3") double deadlineWeight,
        @DefaultValue("30") int deadlineWindowDays) {

    public RecommendationProperties {
        if (categoryWeight < 0 || deadlineWeight < 0) {
            throw new IllegalStateException("gather.recommendation 가중치는 0 이상이어야 합니다.");
        }
        if (deadlineWindowDays <= 0) {
            throw new IllegalStateException(
                    "gather.recommendation.deadline-window-days는 1 이상이어야 합니다.");
        }
    }
}
