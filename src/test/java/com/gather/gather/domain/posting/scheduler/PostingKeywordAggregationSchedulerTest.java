package com.gather.gather.domain.posting.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.service.PostingKeywordRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostingKeywordAggregationSchedulerTest {

    @Mock private PostingKeywordRecommendationService postingKeywordRecommendationService;

    private PostingKeywordAggregationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PostingKeywordAggregationScheduler(postingKeywordRecommendationService);
    }

    @Test
    @DisplayName("aggregateKeywords still runs cleanup when aggregate throws")
    void aggregateKeywords_runsCleanup_whenAggregateThrows() {
        doThrow(new RuntimeException("aggregate failed"))
                .when(postingKeywordRecommendationService)
                .aggregate();

        scheduler.aggregateKeywords();

        verify(postingKeywordRecommendationService).cleanupOldLogs();
    }

    @Test
    @DisplayName("aggregateKeywords does not propagate when cleanup throws")
    void aggregateKeywords_doesNotPropagate_whenCleanupThrows() {
        when(postingKeywordRecommendationService.aggregate()).thenReturn(5);
        doThrow(new RuntimeException("cleanup failed"))
                .when(postingKeywordRecommendationService)
                .cleanupOldLogs();

        scheduler.aggregateKeywords();

        verify(postingKeywordRecommendationService).aggregate();
        verify(postingKeywordRecommendationService).cleanupOldLogs();
    }
}
