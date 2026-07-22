package com.gather.gather.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.posting.entity.PostingRecommendedKeyword;
import com.gather.gather.domain.posting.repository.PostingRecommendedKeywordRepository;
import com.gather.gather.domain.posting.repository.PostingSearchLogRepository;
import com.gather.gather.global.util.NoriKeywordTokenizer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        when(postingSearchLogRepository.findKeywordsBySearchedAtAfter(any()))
                .thenReturn(List.of("유기견보호", "유기견"));
        when(noriKeywordTokenizer.tokenize("유기견보호")).thenReturn(List.of("유기견", "보호"));
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
    @DisplayName("aggregate ignores tokens longer than the recommended-keyword column length")
    void aggregate_ignoresOversizedTokens() {
        String oversizedToken = "가".repeat(51);
        when(postingSearchLogRepository.findKeywordsBySearchedAtAfter(any()))
                .thenReturn(List.of("아무말"));
        when(noriKeywordTokenizer.tokenize("아무말")).thenReturn(List.of(oversizedToken, "아무말"));

        int count = service.aggregate();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostingRecommendedKeyword>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(postingRecommendedKeywordRepository).saveAll(captor.capture());

        assertThat(count).isEqualTo(1);
        assertThat(captor.getValue())
                .extracting(PostingRecommendedKeyword::getKeyword)
                .containsExactly("아무말");
    }

    @ParameterizedTest
    @ValueSource(strings = {"봉사", "활동", "모집", "신청", "참여", "공고"})
    @DisplayName("aggregate excludes stopword tokens that are too generic to recommend")
    void aggregate_excludesStopwordTokens(String stopword) {
        String logKeyword = "유기견" + stopword;
        when(postingSearchLogRepository.findKeywordsBySearchedAtAfter(any()))
                .thenReturn(List.of(logKeyword));
        when(noriKeywordTokenizer.tokenize(logKeyword)).thenReturn(List.of("유기견", stopword));

        int count = service.aggregate();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PostingRecommendedKeyword>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(postingRecommendedKeywordRepository).saveAll(captor.capture());

        assertThat(count).isEqualTo(1);
        assertThat(captor.getValue())
                .extracting(PostingRecommendedKeyword::getKeyword)
                .containsExactly("유기견");
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
