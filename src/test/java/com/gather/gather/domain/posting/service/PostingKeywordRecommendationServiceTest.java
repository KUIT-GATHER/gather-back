package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.entity.PostingRecommendedKeyword;
import com.gather.gather.domain.posting.entity.PostingSearchLog;
import com.gather.gather.domain.posting.repository.PostingRecommendedKeywordRepository;
import com.gather.gather.domain.posting.repository.PostingSearchLogRepository;
import com.gather.gather.domain.posting.service.support.NoriKeywordTokenizer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostingKeywordRecommendationServiceTest {

    @Mock private PostingSearchLogRepository postingSearchLogRepository;
    @Mock private PostingRecommendedKeywordRepository postingRecommendedKeywordRepository;
    @Mock private NoriKeywordTokenizer noriKeywordTokenizer;

    private PostingKeywordRecommendationService service;

    @BeforeEach
    void setUp() {
        service =
                new PostingKeywordRecommendationService(
                        postingSearchLogRepository,
                        postingRecommendedKeywordRepository,
                        noriKeywordTokenizer);
    }

    @Test
    @DisplayName("aggregate counts tokens across logs and replaces the table with the top keywords")
    void aggregate_ranksTokensByFrequencyAndReplacesTable() {
        PostingSearchLog log1 = PostingSearchLog.builder().keyword("유기견봉사").build();
        PostingSearchLog log2 = PostingSearchLog.builder().keyword("유기견").build();
        when(postingSearchLogRepository.findAllBySearchedAtAfter(any()))
                .thenReturn(List.of(log1, log2));
        when(noriKeywordTokenizer.tokenize("유기견봉사")).thenReturn(List.of("유기견", "봉사"));
        when(noriKeywordTokenizer.tokenize("유기견")).thenReturn(List.of("유기견"));

        int count = service.aggregate();

        verify(postingRecommendedKeywordRepository).deleteAllInBatch();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostingRecommendedKeyword>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(postingRecommendedKeywordRepository).saveAll(captor.capture());
        List<PostingRecommendedKeyword> saved = captor.getValue();

        assertThat(count).isEqualTo(2);
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getKeyword()).isEqualTo("유기견");
        assertThat(saved.get(0).getScore()).isEqualTo(2);
    }

    @Test
    @DisplayName("cleanupOldLogs deletes logs older than the retention window")
    void cleanupOldLogs_deletesOldLogs() {
        service.cleanupOldLogs();

        verify(postingSearchLogRepository).deleteBySearchedAtBefore(any());
    }

    @Test
    @DisplayName("getRecommendedKeywords returns keywords ordered by score descending")
    void getRecommendedKeywords_returnsKeywordsInOrder() {
        PostingRecommendedKeyword first =
                PostingRecommendedKeyword.builder().keyword("유기견").score(5).build();
        PostingRecommendedKeyword second =
                PostingRecommendedKeyword.builder().keyword("봉사").score(3).build();
        when(postingRecommendedKeywordRepository.findAllByOrderByScoreDesc())
                .thenReturn(List.of(first, second));

        List<String> keywords = service.getRecommendedKeywords();

        assertThat(keywords).containsExactly("유기견", "봉사");
    }
}
